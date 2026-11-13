package com.esign.common.error;

public record ErrorResponse(String errorCode, String errorMessage) {
    public static ErrorResponse of(ApiException exception) {
        return new ErrorResponse(exception.errorCode().code(), exception.getMessage());
    }
}
