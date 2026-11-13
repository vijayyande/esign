package com.esign.common.error;

public class ApiException extends RuntimeException {
    private final EsignErrorCode errorCode;

    public ApiException(EsignErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ApiException(EsignErrorCode errorCode, String detail) {
        super(detail == null || detail.isBlank() ? errorCode.message() : detail);
        this.errorCode = errorCode;
    }

    public EsignErrorCode errorCode() {
        return errorCode;
    }
}
