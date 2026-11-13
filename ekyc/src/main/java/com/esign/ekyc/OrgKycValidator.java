package com.esign.ekyc;

import com.esign.common.SpecTexts;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.time.IstTimestamps;
import com.esign.common.xml.BankKycRequest;
import com.esign.common.xml.OrgKycRequest;

public final class OrgKycValidator {
    private OrgKycValidator() {
    }

    public static void validate(OrgKycRequest request) {
        KycChecks.require(request != null, EsignErrorCode.ORG_INVALID_XML);
        KycChecks.require(SpecTexts.KYC_XML_VERSION.equals(request.ver), EsignErrorCode.ORG_INVALID_VERSION);
        try {
            IstTimestamps.parse(request.ts, EsignErrorCode.ORG_INVALID_XML);
        } catch (RuntimeException exception) {
            throw new com.esign.common.error.ApiException(EsignErrorCode.ORG_INVALID_XML);
        }
        KycChecks.require(request.txn != null && request.txn.matches("^[A-Za-z0-9._:-]{1,50}$"), EsignErrorCode.ORG_INVALID_TXN);
        KycChecks.require(request.kycOrgId != null && request.kycOrgId.matches("^[A-Za-z0-9_-]{2,40}$"), EsignErrorCode.ORG_INVALID_ID);
        KycChecks.require(request.orgName != null && !request.orgName.isBlank(), EsignErrorCode.ORG_INVALID_NAME);
        KycChecks.require(request.orgSigName != null && !request.orgSigName.isBlank(), EsignErrorCode.ORG_INVALID_SIGNATORY);
        KycChecks.require(request.orgSigCertId != null && request.orgSigCertId.matches("^[A-Za-z0-9:-]{2,64}$"),
                EsignErrorCode.ORG_INVALID_CERT_ID);
        if (request.employeeId != null) {
            KycChecks.require(request.employeeId.matches("^[A-Za-z0-9_-]{1,32}$"), EsignErrorCode.ORG_INVALID_EMPLOYEE);
        }
        KycChecks.require(request.kycInfo != null, EsignErrorCode.ORG_KYC_MISSING);
        BankKycRequest.KycInfo info = request.kycInfo;
        KycChecks.require(info.name != null && !info.name.isBlank(), EsignErrorCode.ORG_INVALID_PERSON_NAME);
        KycChecks.require(KycChecks.mobile(info.mobile), EsignErrorCode.ORG_INVALID_MOBILE);
        if (info.email != null && !info.email.isBlank()) {
            KycChecks.require(KycChecks.email(info.email), EsignErrorCode.ORG_INVALID_EMAIL);
        }
        KycChecks.require(KycChecks.dob(info.dateOfBirth), EsignErrorCode.ORG_INVALID_DOB);
        KycChecks.require(KycChecks.gender(info.gender), EsignErrorCode.ORG_INVALID_GENDER);
        boolean panPresent = info.pan != null && !info.pan.isBlank();
        boolean aadhaarPresent = info.aadhaar != null && !info.aadhaar.isBlank();
        if (panPresent) {
            KycChecks.require(KycChecks.pan(info.pan), EsignErrorCode.ORG_INVALID_PAN);
        }
        if (aadhaarPresent) {
            KycChecks.require(KycChecks.aadhaar(info.aadhaar), EsignErrorCode.ORG_INVALID_AADHAAR);
        }
        KycChecks.require(panPresent || aadhaarPresent, EsignErrorCode.ORG_INVALID_PAN);
        KycChecks.require(request.photo != null && KycChecks.photoFormat(request.photo.format), EsignErrorCode.ORG_INVALID_PHOTO_FORMAT);
        KycChecks.require(request.photo != null && KycChecks.photo(request.photo.value), EsignErrorCode.ORG_INVALID_PHOTO);
        KycChecks.require(request.physicalVerification != null
                        && info.name != null
                        && request.physicalVerification.toLowerCase().contains(info.name.toLowerCase()),
                EsignErrorCode.ORG_INVALID_PHYSICAL_VERIFICATION);
    }
}
