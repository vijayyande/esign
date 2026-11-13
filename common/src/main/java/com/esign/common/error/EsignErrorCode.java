package com.esign.common.error;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Error catalogue from CCA eSign API Specifications 3.3, section 5.
 * HTTP status is used only by the JSON endpoints. XML protocol responses carry the code
 * in the {@code error} or {@code errorCode} attribute and use HTTP 200.
 */
public enum EsignErrorCode {
    INVALID_REQUEST_FORMAT("101", "Invalid Request Format", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_SIGNER_ID("102", "Invalid Signer ID", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_VERSION("103", "Invalid Version", ErrorCategory.ESIGN_RESPONSE, 400),
    XML_SIGNATURE_FAILED("104", "XML Signature validation failed", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_TRANSACTION_ID("105", "Invalid transaction ID", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_ASP_ID("106", "Invalid ASP ID", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_DIGITAL_SIGNATURE("107", "Invalid Digital Signature", ErrorCategory.ESIGN_RESPONSE, 400),
    DOCUMENT_REQUIRED("108", "Minimum one document is required", ErrorCategory.ESIGN_RESPONSE, 400),
    TOO_MANY_DOCUMENTS("109", "Request exceeds Maximum number of documents allowed", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_TIMESTAMP("110", "Invalid Timestamp", ErrorCategory.ESIGN_RESPONSE, 400),
    INVALID_MAX_WAIT("111", "Invalid Maximum Wait Period", ErrorCategory.ESIGN_RESPONSE, 400),
    DUPLICATE_TRANSACTION_ID("112", "Duplicate Transaction ID", ErrorCategory.ESIGN_RESPONSE, 409),
    USER_TIMEOUT("113", "User Timeout. Maximum Wait Time expired.", ErrorCategory.ESIGN_RESPONSE, 408),
    AUTHENTICATION_FAILED("114", "Authentication failed. User credentials invalid.", ErrorCategory.ESIGN_RESPONSE, 401),
    UNKNOWN_ESIGN("199", "Unknown error / Custom error from ESP", ErrorCategory.ESIGN_RESPONSE, 500),

    INVALID_DOCUMENT_HASH("201", "Invalid Document Hash", ErrorCategory.ESIGN_DOCUMENT, 400),
    INVALID_RESPONSE_SIG_TYPE("202", "Invalid response signature type", ErrorCategory.ESIGN_DOCUMENT, 400),
    INVALID_DOCUMENT_URL("203", "Invalid document URL", ErrorCategory.ESIGN_DOCUMENT, 400),
    INVALID_DOCUMENT_INFO("204", "Invalid document information", ErrorCategory.ESIGN_DOCUMENT, 400),
    INVALID_HASH_ALGORITHM("205", "Invalid hash algorithm", ErrorCategory.ESIGN_DOCUMENT, 400),
    DOCUMENT_CANCELLED("206", "Document cancelled by user", ErrorCategory.ESIGN_DOCUMENT, 400),
    UNKNOWN_DOCUMENT("299", "Unknown error / Custom error from ESP", ErrorCategory.ESIGN_DOCUMENT, 500),

    STATUS_INVALID_FORMAT("301", "Invalid Request Format", ErrorCategory.ESIGN_STATUS, 400),
    STATUS_TXN_NOT_FOUND("302", "Transaction number not found", ErrorCategory.ESIGN_STATUS, 404),
    STATUS_INVALID_VERSION("303", "Invalid Version", ErrorCategory.ESIGN_STATUS, 400),
    UNKNOWN_STATUS("399", "Unknown error / Custom error from ESP", ErrorCategory.ESIGN_STATUS, 500),

    BANK_INVALID_XML("401", "Invalid Bank KYC Request XML", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_SIGNATURE("402", "Invalid Bank Digital Signature or Bank is not registered entity in the eKYC application", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_VERSION("403", "Invalid Version Number", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_TXN("404", "Invalid Transaction Id (Either empty or size not acceptable by the application)", ErrorCategory.BANK_KYC, 400),
    BANK_DUPLICATE_TXN("405", "Duplicate Transaction Id", ErrorCategory.BANK_KYC, 409),
    BANK_INVALID_IFSC("406", "Invalid Bank IFSC Code (Empty or invalid format)", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_NAME("407", "Invalid Bank Name (Not a registered bank or the bank name is not as per the RBI specifications)", ErrorCategory.BANK_KYC, 400),
    BANK_KYC_MISSING("408", "KYC Information Missing", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_COMMON_NAME("409", "Invalid Common Name", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_MOBILE("410", "Invalid Mobile Number", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_EMAIL("411", "Invalid Email Id", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_ADDRESS("412", "Invalid Address", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_STATE("413", "Invalid State/Province", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_COUNTRY("414", "Invalid Country", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_POSTAL("415", "Invalid Postal Code", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_DOB("416", "Invalid Date of Birth", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_PAN("417", "Invalid PAN", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_AADHAAR("418", "Invalid Aadhaar", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_PHOTO_FORMAT("419", "Invalid Photo Format provided", ErrorCategory.BANK_KYC, 400),
    BANK_INVALID_PHOTO("420", "Invalid Photo (Either Missing or photo not as per the defined format in the request)", ErrorCategory.BANK_KYC, 400),
    UNKNOWN_BANK("499", "Unknown error / Custom error from eKYC Service Provider", ErrorCategory.BANK_KYC, 500),

    ORG_INVALID_XML("501", "Invalid Org KYC Request XML", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_SIGNATURE("502", "Invalid Organizational Digital Signature or Org is not registered entity in the eKYC application", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_VERSION("503", "Invalid Version Number", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_TXN("504", "Invalid Transaction Id (Either empty or size not acceptable by the application)", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_DUPLICATE_TXN("505", "Duplicate Transaction Id", ErrorCategory.ORGANISATION_KYC, 409),
    ORG_INVALID_ID("506", "Invalid KYC Org Id (either NULL or the Id is not valid in the application)", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_NAME("507", "Invalid Organization Name", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_SIGNATORY("508", "Invalid Organization Signatory Name", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_CERT_ID("509", "Invalid Organizational Signatory Certificate Identifier", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_KYC_MISSING("510", "KYC Information is Missing", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_EMPLOYEE("511", "Invalid Employee Id", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_PERSON_NAME("512", "Invalid Name", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_MOBILE("513", "Invalid Mobile Number", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_EMAIL("514", "Invalid Email Id", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_DOB("515", "Invalid Date of Birth", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_GENDER("516", "Invalid Gender", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_PAN("517", "Invalid PAN", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_AADHAAR("518", "Invalid Aadhaar", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_PHOTO_FORMAT("519", "Invalid Photo Format", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_PHOTO("520", "Invalid Photo (Either Missing or photo not as per the defined format in the request)", ErrorCategory.ORGANISATION_KYC, 400),
    ORG_INVALID_PHYSICAL_VERIFICATION("521", "Invalid Physical Verification Tag or The Name mentioned in this text and the name provided in the tag doesn't match", ErrorCategory.ORGANISATION_KYC, 400),
    UNKNOWN_ORG("599", "Unknown error / Custom error from eKYC Service Provider", ErrorCategory.ORGANISATION_KYC, 500);

    private static final Map<String, EsignErrorCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(EsignErrorCode::code, Function.identity()));

    private final String code;
    private final String message;
    private final ErrorCategory category;
    private final int httpStatus;

    EsignErrorCode(String code, String message, ErrorCategory category, int httpStatus) {
        this.code = code;
        this.message = message;
        this.category = category;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public ErrorCategory category() {
        return category;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static EsignErrorCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        return BY_CODE.get(code.trim());
    }
}
