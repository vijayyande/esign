package com.esign.ekyc;

import com.esign.common.SpecTexts;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.time.IstTimestamps;
import com.esign.common.xml.BankKycRequest;

public final class BankKycValidator {
    private BankKycValidator() {
    }

    public static void validate(BankKycRequest request) {
        if (request == null) {
            throw new ApiException(EsignErrorCode.BANK_INVALID_XML);
        }
        KycChecks.require(SpecTexts.KYC_XML_VERSION.equals(request.ver), EsignErrorCode.BANK_INVALID_VERSION);
        try {
            IstTimestamps.parse(request.ts, EsignErrorCode.BANK_INVALID_XML);
        } catch (ApiException exception) {
            throw new ApiException(EsignErrorCode.BANK_INVALID_XML);
        }
        KycChecks.require(request.txn != null && request.txn.matches("^[A-Za-z0-9._:-]{1,50}$"), EsignErrorCode.BANK_INVALID_TXN);
        if (request.bankIfscCode != null && !request.bankIfscCode.isBlank()) {
            KycChecks.require(request.bankIfscCode.matches("^[A-Za-z0-9]{11}$"), EsignErrorCode.BANK_INVALID_IFSC);
        }
        KycChecks.require(request.bankName != null && !request.bankName.isBlank() && request.bankName.length() <= 120,
                EsignErrorCode.BANK_INVALID_NAME);
        KycChecks.require(request.accountNumber != null && request.accountNumber.matches("^[0-9]{6,18}$"),
                EsignErrorCode.BANK_INVALID_XML);
        KycChecks.require(request.kycInfo != null, EsignErrorCode.BANK_KYC_MISSING);
        BankKycRequest.KycInfo info = request.kycInfo;
        KycChecks.require(info.name != null && !info.name.isBlank() && info.name.length() <= 120, EsignErrorCode.BANK_INVALID_COMMON_NAME);
        KycChecks.require(KycChecks.mobile(info.mobile), EsignErrorCode.BANK_INVALID_MOBILE);
        if (info.email != null && !info.email.isBlank()) {
            KycChecks.require(KycChecks.email(info.email), EsignErrorCode.BANK_INVALID_EMAIL);
        }
        KycChecks.require(info.address != null && !info.address.isBlank(), EsignErrorCode.BANK_INVALID_ADDRESS);
        KycChecks.require(info.stateProvince != null && !info.stateProvince.isBlank(), EsignErrorCode.BANK_INVALID_STATE);
        KycChecks.require(KycChecks.country(info.country), EsignErrorCode.BANK_INVALID_COUNTRY);
        KycChecks.require(KycChecks.postal(info.country, info.postalCode), EsignErrorCode.BANK_INVALID_POSTAL);
        KycChecks.require(KycChecks.dob(info.dateOfBirth), EsignErrorCode.BANK_INVALID_DOB);
        KycChecks.require(KycChecks.gender(info.gender), EsignErrorCode.BANK_KYC_MISSING);
        if (info.pan != null && !info.pan.isBlank()) {
            KycChecks.require(KycChecks.pan(info.pan), EsignErrorCode.BANK_INVALID_PAN);
        }
        if (info.aadhaar != null && !info.aadhaar.isBlank()) {
            KycChecks.require(KycChecks.aadhaar(info.aadhaar), EsignErrorCode.BANK_INVALID_AADHAAR);
        }
        KycChecks.require(request.photo != null && KycChecks.photoFormat(request.photo.format), EsignErrorCode.BANK_INVALID_PHOTO_FORMAT);
        KycChecks.require(request.photo != null && KycChecks.photo(request.photo.value), EsignErrorCode.BANK_INVALID_PHOTO);
    }
}
