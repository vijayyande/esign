package com.esign.common.web;

import com.esign.common.error.ApiException;
import com.esign.common.error.ErrorResponse;
import com.esign.common.error.EsignErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException exception) {
        return ResponseEntity.status(exception.errorCode().httpStatus()).body(ErrorResponse.of(exception));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> unreadable() {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                EsignErrorCode.INVALID_REQUEST_FORMAT.code(),
                EsignErrorCode.INVALID_REQUEST_FORMAT.message()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return ResponseEntity.status(500).body(new ErrorResponse(
                EsignErrorCode.UNKNOWN_ESIGN.code(),
                EsignErrorCode.UNKNOWN_ESIGN.message()));
    }
}
