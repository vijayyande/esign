package com.esign.ekyc;

import com.esign.common.audit.AuditLog;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.hash.Hashes;
import com.esign.common.time.IstTimestamps;
import com.esign.common.validation.SignerIds;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriberService {
    private final AccountDirectory accounts;
    private final KycSubmissionService submissions;
    private final AuditLog auditLog;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<HistoryItem>> history = new ConcurrentHashMap<>();

    public SubscriberService(AccountDirectory accounts, KycSubmissionService submissions, AuditLog auditLog) {
        this.accounts = accounts;
        this.submissions = submissions;
        this.auditLog = auditLog;
    }

    public SignerAccount enrol(EnrolmentRequest request) {
        if (request == null || SignerIds.parse(request.signerId) == null) {
            throw new ApiException(EsignErrorCode.INVALID_SIGNER_ID);
        }
        if (request.pin == null || request.pin.length() < 6) {
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED, "PIN must be at least 6 characters");
        }
        SignerAccount account = new SignerAccount();
        account.signerId = request.signerId.trim();
        account.pinHash = Hashes.sha256Hex(request.pin);
        account.name = request.name;
        account.mobile = request.mobile;
        account.email = request.email;
        account.address = request.address;
        account.stateProvince = request.stateProvince;
        account.country = request.country;
        account.postalCode = request.postalCode;
        account.pan = request.pan == null || request.pan.isBlank() ? null : request.pan.toUpperCase();
        account.dob = request.dob;
        account.gender = request.gender;
        account.aadhaarLastFour = request.aadhaarLastFour;
        account.ekycType = request.ekycType;
        account.photoBase64 = request.photoBase64;
        account.orgName = request.orgName;
        account.orgUnit = request.orgUnit;
        account.kycOrgId = request.kycOrgId;
        account.orgSigName = request.orgSigName;
        account.orgSigCertId = request.orgSigCertId;
        account.employeeId = request.employeeId;
        account.bankName = request.bankName;
        account.bankIfsc = request.bankIfsc;
        account.accountNumber = request.accountNumber;
        account.bankTxn = request.bankTxn;
        validateEnrolment(account);
        accounts.save(account);
        auditLog.add("ENROL", account.signerId, account.ekycType);
        return account;
    }

    public LoginResponse login(String signerId, String pin) {
        SignerAccount account = accounts.require(signerId);
        if (!Hashes.constantTimeEquals(account.pinHash, Hashes.sha256Hex(pin))) {
            auditLog.add("LOGIN_FAILED", signerId, "");
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED);
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(account.signerId, Instant.now().plusSeconds(1800)));
        auditLog.add("LOGIN", account.signerId, "");
        return new LoginResponse(token, account.signerId);
    }

    public void changePin(String authorization, String currentPin, String newPin) {
        SignerAccount account = accountFor(authorization);
        if (!Hashes.constantTimeEquals(account.pinHash, Hashes.sha256Hex(currentPin))) {
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED);
        }
        if (newPin == null || newPin.length() < 6) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "PIN must be at least 6 characters");
        }
        account.pinHash = Hashes.sha256Hex(newPin);
        auditLog.add("PIN_CHANGE", account.signerId, "");
    }

    public Profile profile(String authorization) {
        return Profile.of(accountFor(authorization));
    }

    public Profile update(String authorization, ProfileUpdate update) {
        SignerAccount account = accountFor(authorization);
        if (update.email() != null && !update.email().isBlank()) {
            KycChecks.require(KycChecks.email(update.email()), EsignErrorCode.BANK_INVALID_EMAIL);
            account.email = update.email();
        }
        if (update.address() != null && !update.address().isBlank()) {
            account.address = update.address();
        }
        if (update.stateProvince() != null && !update.stateProvince().isBlank()) {
            account.stateProvince = update.stateProvince();
        }
        if (update.postalCode() != null && !update.postalCode().isBlank()) {
            KycChecks.require(KycChecks.postal(account.country, update.postalCode()), EsignErrorCode.BANK_INVALID_POSTAL);
            account.postalCode = update.postalCode();
        }
        auditLog.add("PROFILE_UPDATE", account.signerId, "Contact fields updated and require fresh verification");
        return Profile.of(account);
    }

    public List<HistoryItem> history(String authorization) {
        SignerAccount account = accountFor(authorization);
        return List.copyOf(history.getOrDefault(account.signerId, List.of()));
    }

    public void recordSigning(String signerId, String txn, String result) {
        accounts.find(signerId).ifPresent(account -> history
                .computeIfAbsent(account.signerId, ignored -> new ArrayList<>())
                .add(0, new HistoryItem(IstTimestamps.now(), txn, result)));
    }

    private SignerAccount accountFor(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED);
        }
        Session session = sessions.get(authorization.substring("Bearer ".length()).trim());
        if (session == null || Instant.now().isAfter(session.expiry())) {
            throw new ApiException(EsignErrorCode.AUTHENTICATION_FAILED);
        }
        return accounts.getExact(session.signerId());
    }

    private void validateEnrolment(SignerAccount account) {
        KycChecks.require(account.name != null && !account.name.isBlank(), EsignErrorCode.BANK_INVALID_COMMON_NAME);
        KycChecks.require(KycChecks.mobile(account.mobile), EsignErrorCode.BANK_INVALID_MOBILE);
        KycChecks.require(KycChecks.country(account.country), EsignErrorCode.BANK_INVALID_COUNTRY);
        KycChecks.require(account.address != null && !account.address.isBlank(), EsignErrorCode.BANK_INVALID_ADDRESS);
        KycChecks.require(account.stateProvince != null && !account.stateProvince.isBlank(), EsignErrorCode.BANK_INVALID_STATE);
        KycChecks.require(KycChecks.postal(account.country, account.postalCode), EsignErrorCode.BANK_INVALID_POSTAL);
        KycChecks.require(KycChecks.gender(account.gender), EsignErrorCode.ORG_INVALID_GENDER);
        KycChecks.require(KycChecks.dob(account.dob), EsignErrorCode.BANK_INVALID_DOB);
        if (account.email != null && !account.email.isBlank()) {
            KycChecks.require(KycChecks.email(account.email), EsignErrorCode.BANK_INVALID_EMAIL);
        }
        if (account.pan != null) {
            KycChecks.require(KycChecks.pan(account.pan), EsignErrorCode.BANK_INVALID_PAN);
        }
        if (account.aadhaarLastFour != null && !account.aadhaarLastFour.isBlank()) {
            KycChecks.require(KycChecks.aadhaar(account.aadhaarLastFour), EsignErrorCode.BANK_INVALID_AADHAAR);
        }
        KycChecks.require(KycChecks.photo(account.photoBase64), EsignErrorCode.BANK_INVALID_PHOTO);
        if ("BANK".equals(account.ekycType)) {
            KycChecks.require(account.pan != null, EsignErrorCode.BANK_INVALID_PAN);
            KycChecks.require(submissions.bankRecord(account.bankTxn) != null, EsignErrorCode.BANK_KYC_MISSING);
        }
        if ("ORGANISATIONAL".equals(account.ekycType)) {
            KycChecks.require(account.kycOrgId != null && !account.kycOrgId.isBlank(), EsignErrorCode.ORG_INVALID_ID);
            KycChecks.require(account.orgName != null && !account.orgName.isBlank(), EsignErrorCode.ORG_INVALID_NAME);
            KycChecks.require(account.orgSigName != null && !account.orgSigName.isBlank(), EsignErrorCode.ORG_INVALID_SIGNATORY);
            KycChecks.require(account.orgSigCertId != null && !account.orgSigCertId.isBlank(), EsignErrorCode.ORG_INVALID_CERT_ID);
        }
        if ("FOREIGN_NATIONAL".equals(account.ekycType)) {
            KycChecks.require(!"IN".equals(account.country), EsignErrorCode.BANK_INVALID_COUNTRY);
        }
        KycChecks.require(account.ekycType != null && !account.ekycType.isBlank(), EsignErrorCode.INVALID_REQUEST_FORMAT);
    }

    public record EnrolmentRequest(String signerId, String pin, String name, String mobile, String email, String address,
                                   String stateProvince, String country, String postalCode, String pan, String dob,
                                   String gender, String aadhaarLastFour, String ekycType, String photoBase64,
                                   String orgName, String orgUnit, String kycOrgId, String orgSigName, String orgSigCertId,
                                   String employeeId, String bankName, String bankIfsc, String accountNumber, String bankTxn) {
    }

    public record LoginRequest(String signerId, String pin) {
    }

    public record LoginResponse(String token, String signerId) {
    }

    public record PinChange(String currentPin, String newPin) {
    }

    public record ProfileUpdate(String email, String address, String stateProvince, String postalCode) {
    }

    public record HistoryItem(String at, String txn, String result) {
    }

    public record Profile(String signerId, String name, String mobile, String email, String address, String stateProvince,
                          String country, String postalCode, String pan, String dob, String gender, String aadhaarLastFour,
                          String ekycType, String orgName, String kycOrgId) {
        static Profile of(SignerAccount account) {
            return new Profile(account.signerId, account.name, account.mobile, account.email, account.address,
                    account.stateProvince, account.country, account.postalCode, account.pan, account.dob, account.gender,
                    account.aadhaarLastFour, account.ekycType, account.orgName, account.kycOrgId);
        }
    }

    private record Session(String signerId, Instant expiry) {
    }
}
