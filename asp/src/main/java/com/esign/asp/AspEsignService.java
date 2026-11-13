package com.esign.asp;

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
import com.esign.common.xml.EsignRequest;
import com.esign.common.xml.EsignResponse;
import com.esign.common.xml.EsignStatusRequest;
import com.esign.common.xml.XmlCodec;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AspEsignService {
    private final AspProperties properties;
    private final XmlSigner xmlSigner;
    private final XmlHttp http;
    private final AuditLog auditLog;
    private final EsignXmlValidator validator = new EsignXmlValidator(Clock.systemUTC());
    private final ConcurrentHashMap<String, StoredDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SigningTransaction> transactions = new ConcurrentHashMap<>();

    public AspEsignService(AspProperties properties, XmlSigner xmlSigner, XmlHttp http, AuditLog auditLog) {
        this.properties = properties;
        this.xmlSigner = xmlSigner;
        this.http = http;
        this.auditLog = auditLog;
    }

    public StoredDocument store(MultipartFile file, String docInfo) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(EsignErrorCode.DOCUMENT_REQUIRED);
        }
        if (docInfo == null || docInfo.isBlank() || docInfo.length() > SpecTexts.MAX_DOC_INFO_LENGTH) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_INFO);
        }
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            Path directory = Path.of(properties.getDataDir(), id);
            Files.createDirectories(directory);
            Path content = directory.resolve("content.bin");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, content);
            }
            byte[] bytes = Files.readAllBytes(content);
            StoredDocument document = new StoredDocument(
                    id,
                    file.getOriginalFilename() == null ? "document.bin" : file.getOriginalFilename(),
                    docInfo.trim(),
                    Hashes.sha256Hex(bytes),
                    content);
            documents.put(id, document);
            auditLog.add("DOCUMENT_STORED", id, document.fileName);
            return document;
        } catch (IOException exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    public StoredDocument document(String id) {
        StoredDocument document = documents.get(id);
        if (document == null) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_URL);
        }
        return document;
    }

    public List<StoredDocument> documents() {
        return new ArrayList<>(documents.values());
    }

    public CreatedRequest create(SignCommand command) {
        if (command.documentIds() == null || command.documentIds().isEmpty()) {
            throw new ApiException(EsignErrorCode.DOCUMENT_REQUIRED);
        }
        if (command.documentIds().size() > SpecTexts.MAX_DOCUMENTS) {
            throw new ApiException(EsignErrorCode.TOO_MANY_DOCUMENTS);
        }
        String algorithm = command.signingAlgorithm() == null ? "RSA" : command.signingAlgorithm();
        String sigType = command.responseSigType() == null || command.responseSigType().isBlank()
                ? "PKCS7pdf" : command.responseSigType();
        int wait = command.maxWaitPeriod() == null ? 1440 : command.maxWaitPeriod();
        EsignRequest request = new EsignRequest();
        request.ver = SpecTexts.ESIGN_VERSION;
        request.signerid = command.signerId();
        request.ts = IstTimestamps.now();
        request.txn = nextTxn();
        request.maxWaitPeriod = Integer.toString(wait);
        request.aspId = properties.getAspId();
        request.responseUrl = properties.getPublicBaseUrl() + "/api/v1/esign/callback";
        request.redirectUrl = properties.getPublicBaseUrl() + "/api/v1/esign/return";
        request.signingAlgorithm = algorithm;
        request.docs = new EsignRequest.Docs();
        List<String> ids = new ArrayList<>();
        int index = 1;
        for (String documentId : command.documentIds()) {
            StoredDocument document = document(documentId);
            preparePdf(document, sigType, command.signerId(), command);
            EsignRequest.InputHash hash = new EsignRequest.InputHash();
            hash.id = Integer.toString(index++);
            hash.hashAlgorithm = SpecTexts.HASH_ALGORITHM;
            hash.docInfo = document.docInfo;
            hash.docUrl = properties.getPublicBaseUrl() + "/api/v1/documents/" + document.id
                    + (document.preparedPath == null ? "/content" : "/prepared");
            hash.responseSigType = sigType;
            hash.value = document.hashHex;
            request.docs.inputHashes.add(hash);
            ids.add(document.id);
        }
        validator.validateRequest(request, properties.getAspId());
        String signed = xmlSigner.sign(XmlCodec.marshal(request));
        String responseXml = http.postXml(properties.getEspSignUrl(), signed);
        xmlSigner.verify(responseXml, EsignErrorCode.INVALID_DIGITAL_SIGNATURE, EsignErrorCode.XML_SIGNATURE_FAILED);
        EsignResponse response = XmlCodec.unmarshal(responseXml, EsignResponse.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
        if ("0".equals(response.status)) {
            EsignErrorCode code = EsignErrorCode.fromCode(response.error);
            throw new ApiException(code == null ? EsignErrorCode.UNKNOWN_ESIGN : code);
        }
        SigningTransaction transaction = new SigningTransaction(request.txn, response.resCode, response.status,
                response.error == null ? "" : response.error, command.signerId(), ids, "");
        transactions.put(transaction.txn, transaction);
        auditLog.add("ESIGN_REQUESTED", transaction.txn, response.resCode);
        String txnref = TxnRef.encode(transaction.txn, transaction.resCode);
        String url = properties.getEspAuthUrl() + "?txnref=" + URLEncoder.encode(txnref, StandardCharsets.UTF_8);
        return new CreatedRequest(transaction.txn, transaction.resCode, transaction.status, url);
    }

    public void callback(String xml) {
        apply(xml);
        auditLog.add("CALLBACK_RECEIVED", XmlCodec.attribute(xml, "txn"), XmlCodec.attribute(xml, "status"));
    }

    public SigningTransaction refresh(String txn) {
        SigningTransaction transaction = require(txn);
        EsignStatusRequest request = new EsignStatusRequest();
        request.ver = SpecTexts.ESIGN_VERSION;
        request.ts = IstTimestamps.now();
        request.txn = txn;
        request.aspId = properties.getAspId();
        String responseXml = http.postXml(properties.getEspStatusUrl(), xmlSigner.sign(XmlCodec.marshal(request)));
        apply(responseXml);
        return require(txn);
    }

    public SigningTransaction require(String txn) {
        SigningTransaction transaction = transactions.get(txn);
        if (transaction == null) {
            throw new ApiException(EsignErrorCode.STATUS_TXN_NOT_FOUND);
        }
        return transaction;
    }

    public List<SigningTransaction> transactions() {
        return new ArrayList<>(transactions.values());
    }

    public String returnPage(String txnref) {
        String[] parts = TxnRef.decode(txnref);
        try {
            refresh(parts[0]);
        } catch (RuntimeException ignored) {
            // The callback may already have the final state. Show whatever is stored.
        }
        SigningTransaction transaction = transactions.get(parts[0]);
        if (transaction == null) {
            return page("Signing result", """
                    <p class="kicker">Application provider</p>
                    <h1>Signing result</h1>
                    <p class="lead">This transaction was not found.</p>
                    <a class="button secondary" href="/">Sign another document</a>
                    """);
        }
        StringBuilder body = new StringBuilder();
        body.append("<p class=\"kicker\">Application provider</p><h1>Signing result</h1>");
        body.append(statusBadge(transaction));
        if (!transaction.error.isBlank()) {
            EsignErrorCode code = EsignErrorCode.fromCode(transaction.error);
            body.append("<p class=\"lead\">").append(esc(transaction.error)).append(" ")
                    .append(esc(code == null ? "" : code.message())).append("</p>");
        }
        for (String documentId : transaction.documentIds) {
            StoredDocument document = documents.get(documentId);
            if (document == null) {
                continue;
            }
            body.append("<article class=\"card\"><h2>").append(esc(document.docInfo)).append("</h2>");
            if (document.signatureBase64 != null) {
                body.append("<p class=\"lead\">").append(document.verified ? "Signature verified." : "Signature was not verified.")
                        .append("</p>");
            }
            body.append("<div class=\"result-links\">");
            if (document.signedPath != null) {
                body.append("<a class=\"button primary\" href=\"/api/v1/documents/").append(document.id)
                        .append("/signed.pdf\">Download signed PDF</a>");
            }
            if (document.signatureBase64 != null) {
                body.append("<a class=\"button secondary\" href=\"/api/v1/documents/").append(document.id)
                        .append("/signature\">Signature details</a>");
            }
            body.append("</div></article>");
        }
        body.append("<p><a href=\"/\">Sign another document</a></p>");
        return page("Signing result", body.toString());
    }

    private static String statusBadge(SigningTransaction transaction) {
        return switch (transaction.status == null ? "" : transaction.status) {
            case "1" -> "<p class=\"badge ok\">Completed</p>";
            case "2" -> "<p class=\"badge wait\">Waiting for the signer</p>";
            default -> "<p class=\"badge bad\">Not completed</p>";
        };
    }

    private void apply(String xml) {
        xmlSigner.verify(xml, EsignErrorCode.INVALID_DIGITAL_SIGNATURE, EsignErrorCode.XML_SIGNATURE_FAILED);
        EsignResponse response = XmlCodec.unmarshal(xml, EsignResponse.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
        SigningTransaction transaction = require(response.txn);
        transaction.status = response.status;
        transaction.error = response.error == null ? "" : response.error;
        transaction.resCode = response.resCode;
        transaction.certificateBase64 = response.userX509Certificate == null ? "" : response.userX509Certificate;
        if (response.signatures == null) {
            return;
        }
        for (EsignResponse.DocSignature signature : response.signatures.docSignatures) {
            int index;
            try {
                index = Integer.parseInt(signature.id) - 1;
            } catch (NumberFormatException exception) {
                continue;
            }
            if (index < 0 || index >= transaction.documentIds.size()) {
                continue;
            }
            StoredDocument document = documents.get(transaction.documentIds.get(index));
            if (document == null) {
                continue;
            }
            document.signatureBase64 = signature.value;
            document.certificateBase64 = transaction.certificateBase64;
            document.signatureError = signature.error == null ? "" : signature.error;
            document.verified = document.signatureError.isBlank() && verify(document);
            if (document.verified) {
                writeSignedPdf(document);
            }
        }
    }

    public java.util.List<SignedPdf.PageSize> pages(String id) {
        StoredDocument document = document(id);
        try {
            return SignedPdf.pages(Files.readAllBytes(document.path), document.fileName, document.docInfo, "");
        } catch (IOException exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    public byte[] preview(String id, int page) {
        StoredDocument document = document(id);
        try {
            return SignedPdf.preview(Files.readAllBytes(document.path), document.fileName, document.docInfo, "", page);
        } catch (IOException exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    private void preparePdf(StoredDocument document, String sigType, String signerId, SignCommand command) {
        document.responseSigType = sigType;
        document.signedPath = null;
        if (!SignedPdf.embeds(sigType)) {
            document.hashHex = document.originalHashHex;
            document.preparedPath = null;
            return;
        }
        try {
            SignedPdf.Placement placement = new SignedPdf.Placement(
                    command.signaturePage(), command.signatureX(), command.signatureY(),
                    command.signatureWidth(), command.signatureHeight());
            SignedPdf.Slot slot = SignedPdf.prepare(
                    Files.readAllBytes(document.path), document.fileName, document.docInfo, signerId, placement);
            Path prepared = document.path.getParent().resolve("prepared.pdf");
            Files.write(prepared, slot.pdf());
            document.preparedPath = prepared;
            document.contentsHexStart = slot.hexStart();
            document.contentsHexLength = slot.hexLength();
            document.hashHex = slot.hashHex();
        } catch (IOException exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    private void writeSignedPdf(StoredDocument document) {
        if (document.preparedPath == null || !SignedPdf.embeds(document.responseSigType)) {
            return;
        }
        try {
            byte[] cms = Base64.getDecoder().decode(document.signatureBase64);
            byte[] signed = SignedPdf.embed(
                    Files.readAllBytes(document.preparedPath),
                    document.contentsHexStart,
                    document.contentsHexLength,
                    cms);
            Path output = document.path.getParent().resolve("signed.pdf");
            Files.write(output, signed);
            byte[] embedded = SignedPdf.digest(signed);
            if (!MessageDigest.isEqual(embedded, HexFormat.of().parseHex(document.hashHex))) {
                document.pdfError = "Signed PDF byte range no longer matches the ESP hash";
                return;
            }
            document.signedPath = output;
            document.pdfError = "";
        } catch (Exception exception) {
            document.pdfError = exception.getMessage() == null ? "Signed PDF was not written" : exception.getMessage();
        }
    }

    private boolean verify(StoredDocument document) {
        try {
            byte[] hash = HexFormat.of().parseHex(document.hashHex);
            byte[] signature = Base64.getDecoder().decode(document.signatureBase64);
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(document.certificateBase64)));
            return DocumentSignatures.verifies(hash, signature, certificate.getPublicKey(), document.responseSigType);
        } catch (Exception exception) {
            return false;
        }
    }

    private String nextTxn() {
        String day = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.of("Asia/Kolkata"))
                .format(java.time.Instant.now());
        return "ASP" + day + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000));
    }

    private static String page(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en"><head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>""" + esc(title) + """
                </title>
                <link rel="stylesheet" href="/site.css">
                </head><body>
                <header class="bar"><a class="brand" href="/"><span class="mark" aria-hidden="true">e</span> eSign <span>Application provider</span></a><nav class="bar-nav"><a href="/docs.html">Documentation</a><span class="spec">API 3.3</span></nav></header>
                <main class="narrow">""" + body + """
                </main>
                </body></html>
                """;
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;");
    }

    public record SignCommand(List<String> documentIds, String signerId, String signingAlgorithm,
                              String responseSigType, Integer maxWaitPeriod,
                              Integer signaturePage, Float signatureX, Float signatureY,
                              Float signatureWidth, Float signatureHeight) {
    }

    public record CreatedRequest(String txn, String resCode, String status, String authenticationUrl) {
    }

    public static class StoredDocument {
        public final String id;
        public final String fileName;
        public final String docInfo;
        public final String originalHashHex;
        public String hashHex;
        public final Path path;
        public Path preparedPath;
        public Path signedPath;
        public int contentsHexStart;
        public int contentsHexLength;
        public String signatureBase64;
        public String certificateBase64;
        public String responseSigType = "PKCS7pdf";
        public boolean verified;
        public String signatureError = "";
        public String pdfError = "";

        public StoredDocument(String id, String fileName, String docInfo, String hashHex, Path path) {
            this.id = id;
            this.fileName = fileName;
            this.docInfo = docInfo;
            this.originalHashHex = hashHex;
            this.hashHex = hashHex;
            this.path = path;
        }
    }

    public static class SigningTransaction {
        public final String txn;
        public String resCode;
        public String status;
        public String error;
        public final String signerId;
        public final List<String> documentIds;
        public String certificateBase64;

        public SigningTransaction(String txn, String resCode, String status, String error, String signerId,
                                  List<String> documentIds, String certificateBase64) {
            this.txn = txn;
            this.resCode = resCode;
            this.status = status;
            this.error = error;
            this.signerId = signerId;
            this.documentIds = List.copyOf(documentIds);
            this.certificateBase64 = certificateBase64;
        }
    }
}
