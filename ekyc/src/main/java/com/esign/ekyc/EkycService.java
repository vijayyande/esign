package com.esign.ekyc;

import com.esign.common.SpecTexts;
import com.esign.common.audit.AuditLog;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.hash.Hashes;
import com.esign.common.time.IstTimestamps;
import com.esign.common.validation.SignerIds;
import com.esign.common.xml.EkycRequest;
import com.esign.common.xml.EkycResponse;
import com.esign.common.xml.SecondFactorRequest;
import com.esign.common.xml.XmlCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EkycService {
    private static final int MAX_ATTEMPTS = 5;

    private final AccountDirectory accounts;
    private final SubscriberService subscribers;
    private final XmlSigner xmlSigner;
    private final AuditLog auditLog;
    private final Map<String, OtpTicket> otps = new ConcurrentHashMap<>();
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, String> completed = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public EkycService(AccountDirectory accounts, SubscriberService subscribers, XmlSigner xmlSigner, AuditLog auditLog) {
        this.accounts = accounts;
        this.subscribers = subscribers;
        this.xmlSigner = xmlSigner;
        this.auditLog = auditLog;
    }

    public OtpIssued issueOtp(String signerId, String txn, boolean expose) {
        if (SignerIds.parse(signerId) == null) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        accounts.require(signerId);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        otps.put(key(signerId, txn), new OtpTicket(Hashes.sha256Hex(otp), Instant.now().plusSeconds(300)));
        auditLog.add("OTP_ISSUED", txn, "SMS OTP issued for " + signerId);
        return new OtpIssued(true, expose ? otp : null);
    }

    public String handle(String xml) {
        XmlCodec.parse(xml, EsignErrorCode.INVALID_REQUEST_FORMAT);
        xmlSigner.verify(xml, EsignErrorCode.INVALID_DIGITAL_SIGNATURE, EsignErrorCode.XML_SIGNATURE_FAILED);
        EkycRequest request = XmlCodec.unmarshal(xml, EkycRequest.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
        validate(request);
        String cacheKey = key(request.signerid, request.txn);
        if (completed.containsKey(cacheKey)) {
            return failure(request, "Transaction already completed");
        }
        Attempt attempt = attempts.computeIfAbsent(cacheKey, ignored -> new Attempt());
        if (attempt.count >= MAX_ATTEMPTS) {
            auditLog.add("AUTH_LOCKED", request.txn, request.signerid);
            return failure(request, "Authentication failed. Maximum retries exceeded.");
        }
        SignerAccount account;
        try {
            account = accounts.require(request.signerid);
        } catch (ApiException exception) {
            attempt.count++;
            auditLog.add("AUTH_FAILED", request.txn, "Unknown signer");
            return failure(request, EsignErrorCode.AUTHENTICATION_FAILED.message());
        }
        if (!pinMatches(account, request.txn, request.pinhash)) {
            attempt.count++;
            auditLog.add("AUTH_FAILED", request.txn, "PIN mismatch");
            return failure(request, EsignErrorCode.AUTHENTICATION_FAILED.message());
        }
        if (request.secondFactor == null || request.secondFactor.isBlank()) {
            attempt.pinAccepted = true;
            auditLog.add("AUTH_PENDING", request.txn, "Waiting for second factor");
            return response(request, "2", "", null);
        }
        String factorError = checkSecondFactor(request, account);
        if (factorError != null) {
            attempt.count++;
            auditLog.add("AUTH_FAILED", request.txn, factorError);
            return failure(request, factorError);
        }
        String respCode = "KYC" + Long.toHexString(random.nextLong()).replace("-", "");
        String body = response(request, "1", "", account);
        EkycResponse parsed = XmlCodec.unmarshal(body, EkycResponse.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
        parsed.respCode = respCode;
        String signed = xmlSigner.sign(XmlCodec.marshal(parsed));
        completed.put(cacheKey, signed);
        subscribers.recordSigning(account.signerId, request.txn, "eKYC authenticated");
        auditLog.add("AUTH_SUCCESS", request.txn, respCode);
        return signed;
    }

    public String failure(String xml, String message) {
        EkycRequest request = new EkycRequest();
        request.txn = XmlCodec.attribute(xml, "txn");
        request.signerid = XmlCodec.attribute(xml, "signerid");
        return failure(request, message);
    }

    private String failure(EkycRequest request, String message) {
        return response(request, "0", message, null);
    }

    private String response(EkycRequest request, String status, String error, SignerAccount account) {
        EkycResponse response = new EkycResponse();
        response.ver = SpecTexts.ESIGN_VERSION;
        response.status = status;
        response.signerid = request.signerid;
        response.ts = IstTimestamps.now();
        response.txn = request.txn;
        response.error = error == null || error.isBlank() ? null : error;
        response.respCode = "KYC" + Integer.toHexString(request.txn == null ? 0 : request.txn.hashCode());
        if (account != null && "1".equals(status)) {
            response.kycData = new EkycResponse.KycData();
            response.kycData.name = account.name;
            response.kycData.mobile = account.mobile;
            response.kycData.email = account.email;
            response.kycData.address = account.address;
            response.kycData.stateProvince = account.stateProvince;
            response.kycData.country = account.country;
            response.kycData.postalCode = account.postalCode;
            response.kycData.pan = account.pan;
            response.kycData.dob = account.dob;
            response.kycData.gender = account.gender;
            response.kycData.aadhaar = account.aadhaarLastFour;
            response.kycData.eKYCtype = account.ekycType;
            response.photo = account.photoBase64;
            if ("BANK".equals(account.ekycType)) {
                response.bankDetails = new EkycResponse.BankDetails();
                response.bankDetails.txn = account.bankTxn;
                response.bankDetails.bankIfscCode = account.bankIfsc;
                response.bankDetails.bankName = account.bankName;
                response.bankDetails.accountNumber = account.accountNumber;
            }
            if ("ORGANISATIONAL".equals(account.ekycType)) {
                response.organisationDetails = new EkycResponse.OrganisationDetails();
                response.organisationDetails.txn = request.txn;
                response.organisationDetails.orgName = account.orgName;
                response.organisationDetails.orgUnit = account.orgUnit;
                response.organisationDetails.kycOrgId = account.kycOrgId;
                response.organisationDetails.orgSigName = account.orgSigName;
                response.organisationDetails.orgSigCertId = account.orgSigCertId;
            }
        }
        return xmlSigner.sign(XmlCodec.marshal(response));
    }

    private void validate(EkycRequest request) {
        if (request == null || !SpecTexts.ESIGN_VERSION.equals(request.ver)) {
            throw new ApiException(EsignErrorCode.INVALID_VERSION);
        }
        if (SignerIds.parse(request.signerid) == null) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        IstTimestamps.requireFresh(request.ts, Instant.now(), EsignErrorCode.INVALID_TIMESTAMP);
        if (request.txn == null || !request.txn.matches("^[A-Za-z0-9._:-]{1,50}$")) {
            throw new ApiException(EsignErrorCode.INVALID_TRANSACTION_ID);
        }
        if (request.pinhash == null || !request.pinhash.matches("^[0-9a-f]{64}$")) {
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED);
        }
    }

    private String checkSecondFactor(EkycRequest request, SignerAccount account) {
        String xml;
        try {
            xml = new String(Base64.getDecoder().decode(request.secondFactor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "Invalid second factor";
        }
        SecondFactorRequest factor = XmlCodec.unmarshal(xml, SecondFactorRequest.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
        if (!"1.0".equals(factor.ver)) {
            return "Invalid Version";
        }
        if (!request.signerid.equals(factor.signerid) || !request.txn.equals(factor.txn)) {
            return "Invalid transaction ID";
        }
        if (!SpecTexts.KYC_CONSENT.equals(factor.consent)) {
            return "Consent text does not match";
        }
        if (!"SMSOTP".equals(factor.authenticationType)) {
            return "Authentication type is not enabled. This reference service enables SMSOTP.";
        }
        OtpTicket ticket = otps.get(key(account.signerId, request.txn));
        if (ticket == null) {
            ticket = otps.get(key(request.signerid, request.txn));
        }
        if (ticket == null || Instant.now().isAfter(ticket.expiry())) {
            return "OTP expired or was not requested";
        }
        if (!Hashes.constantTimeEquals(ticket.hash(), factor.smsOtp)) {
            return EsignErrorCode.AUTHENTICATION_FAILED.message();
        }
        return null;
    }

    private static String key(String signerId, String txn) {
        return signerId + "|" + txn;
    }

    /**
     * The stored value is already hash(pin). The request carries hash(txn + hash(pin)).
     */
    private boolean pinMatches(SignerAccount account, String txn, String presentedPinHash) {
        String expected = Hashes.sha256Hex(txn + account.pinHash);
        return Hashes.constantTimeEquals(expected, presentedPinHash);
    }

    private record OtpTicket(String hash, Instant expiry) {
    }

    private static final class Attempt {
        private int count;
        private boolean pinAccepted;
    }

    public record OtpIssued(boolean sent, String demoOtp) {
    }
}
