package com.esign.esp;

import com.esign.common.SpecTexts;
import com.esign.common.TxnRef;
import com.esign.common.audit.AuditLog;
import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.hash.Hashes;
import com.esign.common.http.XmlHttp;
import com.esign.common.time.IstTimestamps;
import com.esign.common.validation.EsignXmlValidator;
import com.esign.common.validation.ParsedSignerId;
import com.esign.common.validation.SignerIds;
import com.esign.common.xml.EkycRequest;
import com.esign.common.xml.EkycResponse;
import com.esign.common.xml.EsignRequest;
import com.esign.common.xml.EsignResponse;
import com.esign.common.xml.EsignStatusRequest;
import com.esign.common.xml.SecondFactorRequest;
import com.esign.common.xml.XmlCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EsignGatewayService {
    private static final Logger log = LoggerFactory.getLogger(EsignGatewayService.class);

    private final EspProperties properties;
    private final XmlSigner xmlSigner;
    private final XmlHttp http;
    private final AuditLog auditLog;
    private final ObjectMapper json;
    private final EsignXmlValidator validator = new EsignXmlValidator(Clock.systemUTC());
    private final AspRegistry asps;
    private final ConcurrentHashMap<String, EsignTransaction> transactions = new ConcurrentHashMap<>();

    public EsignGatewayService(EspProperties properties, XmlSigner xmlSigner, XmlHttp http, AuditLog auditLog,
                               ObjectMapper json, AspRegistry asps) {
        this.properties = properties;
        this.xmlSigner = xmlSigner;
        this.http = http;
        this.auditLog = auditLog;
        this.json = json;
        this.asps = asps;
    }

    public String submit(String xml) {
        try {
            XmlCodec.parse(xml, EsignErrorCode.INVALID_REQUEST_FORMAT);
            java.security.cert.X509Certificate signer = xmlSigner.verifyAndCertificate(
                    xml, EsignErrorCode.INVALID_DIGITAL_SIGNATURE, EsignErrorCode.XML_SIGNATURE_FAILED);
            EsignRequest request = XmlCodec.unmarshal(xml, EsignRequest.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
            asps.require(request.aspId, signer, EsignErrorCode.INVALID_ASP_ID, EsignErrorCode.XML_SIGNATURE_FAILED);
            validator.validateRequest(request, request.aspId);
            if (request.signerid != null && !request.signerid.isBlank()) {
                ParsedSignerId parsed = SignerIds.parse(request.signerid);
                if (parsed == null || !properties.getEspId().equals(parsed.espId())) {
                    throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
                }
            }
            EsignTransaction existing = transactions.get(request.txn);
            if (existing != null) {
                throw new ApiException(EsignErrorCode.DUPLICATE_TRANSACTION_ID);
            }
            String resCode = "ESP" + Long.toHexString(System.nanoTime());
            Instant deadline = Instant.now().plus(Long.parseLong(request.maxWaitPeriod), ChronoUnit.MINUTES);
            List<EsignTransaction.DocInput> docs = request.docs.inputHashes.stream()
                    .map(hash -> new EsignTransaction.DocInput(
                            hash.id, hash.value.trim().toLowerCase(), hash.docInfo, hash.docUrl, hash.responseSigType))
                    .toList();
            EsignTransaction transaction = new EsignTransaction(
                    request.txn, resCode, request.aspId, blankToNull(request.signerid), request.responseUrl,
                    blankToNull(request.redirectUrl), request.signingAlgorithm, deadline, docs);
            if (transactions.putIfAbsent(request.txn, transaction) != null) {
                throw new ApiException(EsignErrorCode.DUPLICATE_TRANSACTION_ID);
            }
            auditLog.add("ESIGN_ACCEPTED", request.txn, resCode);
            return render(transaction);
        } catch (ApiException exception) {
            auditLog.add("ESIGN_REJECTED", XmlCodec.attribute(xml, "txn"), exception.errorCode().code());
            return error(XmlCodec.attribute(xml, "txn"), exception.errorCode());
        }
    }

    public String status(String xml) {
        try {
            java.security.cert.X509Certificate signer = xmlSigner.verifyAndCertificate(
                    xml, EsignErrorCode.STATUS_INVALID_FORMAT, EsignErrorCode.STATUS_INVALID_FORMAT);
            EsignStatusRequest request = XmlCodec.unmarshal(xml, EsignStatusRequest.class, EsignErrorCode.STATUS_INVALID_FORMAT);
            validator.validateStatus(request, null);
            asps.require(request.aspId, signer, EsignErrorCode.STATUS_INVALID_FORMAT, EsignErrorCode.STATUS_INVALID_FORMAT);
            EsignTransaction transaction = transactions.get(request.txn);
            if (transaction == null || !transaction.aspId.equals(request.aspId)) {
                throw new ApiException(EsignErrorCode.STATUS_TXN_NOT_FOUND);
            }
            auditLog.add("STATUS_READ", transaction.txn, transaction.status);
            return render(transaction);
        } catch (ApiException exception) {
            auditLog.add("STATUS_REJECTED", XmlCodec.attribute(xml, "txn"), exception.errorCode().code());
            return error(XmlCodec.attribute(xml, "txn"), exception.errorCode());
        }
    }

    public OtpView sendOtp(String txnref, String signerId) {
        EsignTransaction transaction = requirePending(txnref, signerId);
        String body = http.postJson(properties.getOtpUrl(),
                "{\"signerId\":\"" + jsonEscape(signerId) + "\",\"txn\":\"" + jsonEscape(transaction.txn) + "\"}");
        try {
            OtpView view = json.readValue(body, OtpView.class);
            if (!view.sent()) {
                throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, "OTP was not issued");
            }
            auditLog.add("OTP_REQUESTED", transaction.txn, signerId);
            return view;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, "OTP service returned an unexpected payload");
        }
    }

    public AuthResult authenticate(String txnref, String signerId, String pin, String otp, List<String> signedDocIds) {
        EsignTransaction transaction = requirePending(txnref, signerId);
        synchronized (transaction) {
            if (!"2".equals(transaction.status)) {
                return AuthResult.done(transaction);
            }
            if (Instant.now().isAfter(transaction.deadline)) {
                fail(transaction, EsignErrorCode.USER_TIMEOUT);
                dispatch(transaction);
                return AuthResult.done(transaction);
            }
            List<EsignTransaction.DocInput> selected = transaction.documents.stream()
                    .filter(doc -> signedDocIds.contains(doc.id()))
                    .toList();
            if (selected.isEmpty()) {
                transaction.results.clear();
                transaction.documents.forEach(doc -> transaction.results.add(
                        new EsignTransaction.DocOutput(doc.id(), "", EsignErrorCode.DOCUMENT_CANCELLED.code())));
                fail(transaction, EsignErrorCode.DOCUMENT_CANCELLED);
                dispatch(transaction);
                return AuthResult.done(transaction);
            }
            try {
                EkycResponse kyc = authenticateSigner(transaction, signerId, pin, otp);
                Issued issued = signWithCa(transaction, kyc, selected);
                transaction.certificateBase64 = issued.certificateBase64();
                transaction.results.clear();
                for (EsignTransaction.DocInput document : transaction.documents) {
                    IssuedSignature signature = issued.signatures().stream()
                            .filter(item -> document.id().equals(item.id()))
                            .findFirst()
                            .orElse(null);
                    if (!signedDocIds.contains(document.id())) {
                        transaction.results.add(new EsignTransaction.DocOutput(
                                document.id(), "", EsignErrorCode.DOCUMENT_CANCELLED.code()));
                    } else if (signature == null || signature.error() != null && !signature.error().isBlank()) {
                        transaction.results.add(new EsignTransaction.DocOutput(
                                document.id(), "", signature == null ? EsignErrorCode.UNKNOWN_DOCUMENT.code() : signature.error()));
                    } else if (!signatureVerifies(document, signature.signatureBase64(), issued.certificateBase64())) {
                        transaction.results.add(new EsignTransaction.DocOutput(
                                document.id(), "", EsignErrorCode.INVALID_DIGITAL_SIGNATURE.code()));
                    } else {
                        transaction.results.add(new EsignTransaction.DocOutput(document.id(), signature.signatureBase64(), ""));
                    }
                }
                boolean anySigned = transaction.results.stream().anyMatch(result -> result.error().isBlank());
                transaction.status = anySigned ? "1" : "0";
                transaction.error = anySigned ? "" : EsignErrorCode.UNKNOWN_DOCUMENT.code();
                auditLog.add("ESIGN_COMPLETED", transaction.txn, transaction.status);
            } catch (ApiException exception) {
                fail(transaction, exception.errorCode());
            }
            dispatch(transaction);
            return AuthResult.done(transaction);
        }
    }

    public EsignTransaction requirePending(String txnref, String signerId) {
        String[] parts;
        try {
            parts = TxnRef.decode(txnref);
        } catch (RuntimeException exception) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT);
        }
        EsignTransaction transaction = transactions.get(parts[0]);
        if (transaction == null || !transaction.resCode.equals(parts[1])) {
            throw new ApiException(EsignErrorCode.STATUS_TXN_NOT_FOUND);
        }
        if (transaction.requestSignerId != null && !transaction.requestSignerId.equals(signerId)) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        ParsedSignerId parsed = SignerIds.parse(signerId);
        if (parsed == null || !properties.getEspId().equals(parsed.espId())) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        return transaction;
    }

    public List<EsignTransaction> transactions() {
        return transactions.values().stream()
                .sorted((left, right) -> right.createdAt.compareTo(left.createdAt))
                .toList();
    }

    public EsignTransaction transaction(String txn) {
        EsignTransaction transaction = transactions.get(txn);
        if (transaction == null) {
            throw new ApiException(EsignErrorCode.STATUS_TXN_NOT_FOUND);
        }
        return transaction;
    }

    @Scheduled(fixedDelay = 30_000)
    public void expire() {
        Instant now = Instant.now();
        for (EsignTransaction transaction : transactions.values()) {
            if ("2".equals(transaction.status) && now.isAfter(transaction.deadline)) {
                synchronized (transaction) {
                    if ("2".equals(transaction.status)) {
                        fail(transaction, EsignErrorCode.USER_TIMEOUT);
                        dispatch(transaction);
                    }
                }
            }
        }
    }

    private EkycResponse authenticateSigner(EsignTransaction transaction, String signerId, String pin, String otp) {
        EkycRequest request = new EkycRequest();
        request.ver = SpecTexts.ESIGN_VERSION;
        request.signerid = signerId;
        request.ts = IstTimestamps.now();
        request.txn = transaction.txn;
        request.pinhash = Hashes.pinHash(transaction.txn, pin);
        SecondFactorRequest factor = new SecondFactorRequest();
        factor.ver = SpecTexts.KYC_XML_VERSION;
        factor.signerid = signerId;
        factor.ts = request.ts;
        factor.txn = transaction.txn;
        factor.authenticationType = "SMSOTP";
        factor.consent = SpecTexts.KYC_CONSENT;
        factor.smsOtp = Hashes.sha256Hex(otp);
        request.secondFactor = Base64.getEncoder().encodeToString(
                XmlCodec.marshal(factor).getBytes(StandardCharsets.UTF_8));
        String responseXml = http.postXml(properties.getEkycUrl(), xmlSigner.sign(XmlCodec.marshal(request)));
        xmlSigner.verify(responseXml, EsignErrorCode.INVALID_DIGITAL_SIGNATURE, EsignErrorCode.XML_SIGNATURE_FAILED);
        EkycResponse response = XmlCodec.unmarshal(responseXml, EkycResponse.class, EsignErrorCode.UNKNOWN_ESIGN);
        if (!"1".equals(response.status) || response.kycData == null || response.kycData.name == null) {
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED);
        }
        return response;
    }

    private Issued signWithCa(EsignTransaction transaction, EkycResponse kyc, List<EsignTransaction.DocInput> selected) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("subjectName", kyc.kycData.name);
            body.put("signingAlgorithm", transaction.signingAlgorithm);
            body.put("documents", selected.stream().map(doc -> Map.of(
                    "id", doc.id(),
                    "hashHex", doc.hashHex(),
                    "responseSigType", doc.responseSigType())).toList());
            String response = http.postJson(properties.getCaUrl(), json.writeValueAsString(body));
            return json.readValue(response, Issued.class);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    private static boolean signatureVerifies(EsignTransaction.DocInput document, String signatureBase64, String certificateBase64) {
        try {
            byte[] hash = HexFormat.of().parseHex(document.hashHex());
            byte[] signature = Base64.getDecoder().decode(signatureBase64);
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(certificateBase64)));
            return DocumentSignatures.verifies(hash, signature, certificate.getPublicKey(), document.responseSigType());
        } catch (Exception exception) {
            return false;
        }
    }

    private void fail(EsignTransaction transaction, EsignErrorCode error) {
        transaction.status = "0";
        transaction.error = error.code();
        auditLog.add("ESIGN_FAILED", transaction.txn, error.code());
    }

    private void dispatch(EsignTransaction transaction) {
        String payload = render(transaction);
        try {
            http.postXml(transaction.responseUrl, payload);
            transaction.callbackDelivered = true;
            auditLog.add("CALLBACK", transaction.txn, transaction.responseUrl);
        } catch (RuntimeException exception) {
            transaction.callbackError = exception.getMessage();
            auditLog.add("CALLBACK_FAILED", transaction.txn, exception.getMessage());
            log.warn("Callback failed for {}: {}", transaction.txn, exception.getMessage());
        }
    }

    private String render(EsignTransaction transaction) {
        EsignResponse response = new EsignResponse();
        response.ver = SpecTexts.ESIGN_VERSION;
        response.status = transaction.status;
        response.ts = IstTimestamps.now();
        response.txn = transaction.txn;
        response.resCode = transaction.resCode;
        response.error = transaction.error == null || transaction.error.isBlank() ? null : transaction.error;
        if ("1".equals(transaction.status)) {
            response.userX509Certificate = transaction.certificateBase64;
            response.signatures = new EsignResponse.Signatures();
            for (EsignTransaction.DocOutput result : transaction.results) {
                EsignResponse.DocSignature signature = new EsignResponse.DocSignature();
                signature.id = result.id();
                signature.sigHashAlgorithm = SpecTexts.HASH_ALGORITHM;
                signature.error = result.error().isBlank() ? null : result.error();
                signature.value = result.signatureBase64();
                response.signatures.docSignatures.add(signature);
            }
        }
        return xmlSigner.sign(XmlCodec.marshal(response));
    }

    private String error(String txn, EsignErrorCode error) {
        EsignResponse response = new EsignResponse();
        response.ver = SpecTexts.ESIGN_VERSION;
        response.status = "0";
        response.ts = IstTimestamps.now();
        response.txn = txn;
        response.resCode = "ERR" + Integer.toHexString(txn.hashCode());
        response.error = error.code();
        return xmlSigner.sign(XmlCodec.marshal(response));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record OtpView(boolean sent, String demoOtp) {
    }

    public record Issued(String certificateBase64, String certificatePem, String caCertificateBase64,
                         List<IssuedSignature> signatures) {
    }

    public record IssuedSignature(String id, String signatureBase64, String sigHashAlgorithm, String error) {
    }

    public record AuthResult(EsignTransaction transaction) {
        static AuthResult done(EsignTransaction transaction) {
            return new AuthResult(transaction);
        }
    }
}
