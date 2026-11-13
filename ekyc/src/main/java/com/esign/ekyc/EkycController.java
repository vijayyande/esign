package com.esign.ekyc;

import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class EkycController {
    private final EkycService ekycService;
    private final KycSubmissionService submissions;
    private final SubscriberService subscribers;
    private final EkycProperties properties;
    private final XmlSigner xmlSigner;

    public EkycController(EkycService ekycService, KycSubmissionService submissions, SubscriberService subscribers,
                          EkycProperties properties, XmlSigner xmlSigner) {
        this.ekycService = ekycService;
        this.submissions = submissions;
        this.subscribers = subscribers;
        this.properties = properties;
        this.xmlSigner = xmlSigner;
    }

    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of("component", "eKYC", "specVersion", "3.3", "port", "8083");
    }

    @GetMapping(value = "/meta/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String certificate() {
        return DocumentSignatures.pem(xmlSigner.certificate());
    }

    @PostMapping(value = "/ekyc", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> ekyc(@RequestBody String xml) {
        try {
            return ResponseEntity.ok(ekycService.handle(xml));
        } catch (ApiException exception) {
            return ResponseEntity.ok(ekycService.failure(xml, exception.getMessage()));
        } catch (RuntimeException exception) {
            return ResponseEntity.ok(ekycService.failure(xml, EsignErrorCode.UNKNOWN_ESIGN.message()));
        }
    }

    @PostMapping("/otp")
    public EkycService.OtpIssued otp(@RequestBody OtpRequest request) {
        return ekycService.issueOtp(request.signerId(), request.txn(), properties.isExposeDemoOtp());
    }

    @PostMapping(value = "/bank-kyc", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> bank(@RequestBody String xml) {
        try {
            return ResponseEntity.ok(submissions.submitBank(xml));
        } catch (ApiException exception) {
            return ResponseEntity.ok(submissions.bankFailure(xml, exception));
        }
    }

    @PostMapping(value = "/org-kyc", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> org(@RequestBody String xml) {
        try {
            return ResponseEntity.ok(submissions.submitOrg(xml));
        } catch (ApiException exception) {
            return ResponseEntity.ok(submissions.orgFailure(xml, exception));
        }
    }

    @PostMapping("/accounts")
    public SubscriberService.Profile enrol(@RequestBody SubscriberService.EnrolmentRequest request) {
        return SubscriberService.Profile.of(subscribers.enrol(request));
    }

    @PostMapping("/subscriber/login")
    public SubscriberService.LoginResponse login(@RequestBody SubscriberService.LoginRequest request) {
        return subscribers.login(request.signerId(), request.pin());
    }

    @PostMapping("/subscriber/pin")
    public Map<String, String> changePin(@RequestHeader("Authorization") String authorization,
                                         @RequestBody SubscriberService.PinChange request) {
        subscribers.changePin(authorization, request.currentPin(), request.newPin());
        return Map.of("status", "1");
    }

    @GetMapping("/subscriber/profile")
    public SubscriberService.Profile profile(@RequestHeader("Authorization") String authorization) {
        return subscribers.profile(authorization);
    }

    @PostMapping("/subscriber/profile")
    public SubscriberService.Profile update(@RequestHeader("Authorization") String authorization,
                                            @RequestBody SubscriberService.ProfileUpdate update) {
        return subscribers.update(authorization, update);
    }

    @GetMapping("/subscriber/history")
    public java.util.List<SubscriberService.HistoryItem> history(@RequestHeader("Authorization") String authorization) {
        return subscribers.history(authorization);
    }

    public record OtpRequest(String signerId, String txn) {
    }
}
