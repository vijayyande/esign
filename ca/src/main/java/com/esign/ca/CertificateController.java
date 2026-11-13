package com.esign.ca;

import com.esign.common.crypto.DocumentSignatures;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CertificateController {
    private final CertificateService certificates;

    public CertificateController(CertificateService certificates) {
        this.certificates = certificates;
    }

    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of("component", "CA", "specVersion", "3.3", "port", "8084");
    }

    @GetMapping(value = "/ca/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String caCertificate() {
        return DocumentSignatures.pem(certificates.caCertificate());
    }

    @PostMapping("/certificates")
    public CertificateService.Issued issue(@RequestBody CertificateService.IssueRequest request) {
        return certificates.issue(request);
    }
}
