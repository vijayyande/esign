package com.esign.common.web;

import com.esign.common.error.ErrorCodeInfo;
import com.esign.common.error.EsignErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class ErrorCodeController {
    @GetMapping("/api/v1/meta/error-codes")
    public List<ErrorCodeInfo> errorCodes() {
        return Arrays.stream(EsignErrorCode.values())
                .map(code -> new ErrorCodeInfo(code.code(), code.message(), code.category().name()))
                .toList();
    }
}
