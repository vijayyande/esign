package com.esign.esp;

import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.EsignErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.esign.common.TxnRef;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class EsignController {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Kolkata"));

    private final EsignGatewayService gateway;
    private final AuthPage authPage;
    private final XmlSigner xmlSigner;
    private final EspProperties properties;
    private final AspRegistry asps;

    public EsignController(EsignGatewayService gateway, AuthPage authPage, XmlSigner xmlSigner, EspProperties properties,
                           AspRegistry asps) {
        this.gateway = gateway;
        this.authPage = authPage;
        this.xmlSigner = xmlSigner;
        this.properties = properties;
        this.asps = asps;
    }

    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of("component", "ESP", "specVersion", "3.3", "port", "8082");
    }

    @GetMapping(value = "/meta/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String certificate() {
        return DocumentSignatures.pem(xmlSigner.certificate());
    }

    @GetMapping("/meta/service")
    public Map<String, Object> service() {
        return Map.of(
                "espId", properties.getEspId(),
                "onboardedAspIds", asps.ids(),
                "ekycUrl", properties.getEkycUrl(),
                "otpUrl", properties.getOtpUrl(),
                "caUrl", properties.getCaUrl());
    }

    @GetMapping("/esign/transactions")
    public List<TransactionSummary> transactions() {
        return gateway.transactions().stream().map(TransactionSummary::of).toList();
    }

    @GetMapping("/esign/transactions/{txn}")
    public TransactionDetail transaction(@PathVariable String txn) {
        return TransactionDetail.of(gateway.transaction(txn));
    }

    @PostMapping(value = "/esign", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> esign(@RequestBody String xml) {
        return ResponseEntity.ok(gateway.submit(xml));
    }

    @PostMapping(value = "/esign/status", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> status(@RequestBody String xml) {
        return ResponseEntity.ok(gateway.status(xml));
    }

    @GetMapping(value = "/esign/auth", produces = MediaType.TEXT_HTML_VALUE)
    public String auth(@RequestParam String txnref) {
        return authPage.page(txnref);
    }

    @PostMapping(value = "/esign/auth", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String authSubmit(@RequestParam String txnref,
                             @RequestParam String signerId,
                             @RequestParam String pin,
                             @RequestParam String otp,
                             @RequestParam(name = "doc", required = false) List<String> docs) {
        return authPage.submit(txnref, signerId, pin, otp, docs);
    }

    @PostMapping("/esign/auth/otp")
    public EsignGatewayService.OtpView otp(@RequestBody OtpRequest request) {
        return gateway.sendOtp(request.txnref(), request.signerId());
    }

    @GetMapping("/asps")
    public List<AspRegistry.AspView> asps() {
        return asps.list();
    }

    @PostMapping("/asps")
    public AspRegistry.AspView onboard(@RequestBody OnboardRequest request) {
        return asps.onboard(request.aspId(), request.name(), request.certificatePem());
    }

    @PostMapping("/asps/fetch")
    public AspRegistry.AspView onboardFromUrl(@RequestBody FetchRequest request) {
        return asps.onboardFromUrl(request.aspId(), request.name(), request.certificateUrl());
    }

    @DeleteMapping("/asps/{aspId}")
    public void revoke(@PathVariable String aspId) {
        asps.revoke(aspId);
    }

    public record OnboardRequest(String aspId, String name, String certificatePem) {
    }

    public record FetchRequest(String aspId, String name, String certificateUrl) {
    }

    public record OtpRequest(String txnref, String signerId) {
    }

    public record TransactionSummary(String txn, String resCode, String aspId, String signerId, String status,
                                     String error, String signingAlgorithm, int documents, String createdAt,
                                     String deadline, boolean callbackDelivered, String callbackError) {
        static TransactionSummary of(EsignTransaction transaction) {
            return new TransactionSummary(
                    transaction.txn, transaction.resCode, transaction.aspId,
                    transaction.requestSignerId == null ? "" : transaction.requestSignerId,
                    transaction.status, transaction.error, transaction.signingAlgorithm, transaction.documents.size(),
                    transaction.createdAt, DEADLINE.format(transaction.deadline),
                    transaction.callbackDelivered, transaction.callbackError);
        }
    }

    public record TransactionDetail(String txn, String resCode, String aspId, String signerId, String status,
                                    String error, String signingAlgorithm, String createdAt, String deadline,
                                    String responseUrl, String redirectUrl, String authenticationUrl,
                                    boolean callbackDelivered, String callbackError, boolean certificateIssued,
                                    List<DocumentView> documents) {
        static TransactionDetail of(EsignTransaction transaction) {
            String txnref = TxnRef.encode(transaction.txn, transaction.resCode);
            return new TransactionDetail(
                    transaction.txn, transaction.resCode, transaction.aspId,
                    transaction.requestSignerId == null ? "" : transaction.requestSignerId,
                    transaction.status, transaction.error, transaction.signingAlgorithm, transaction.createdAt,
                    DEADLINE.format(transaction.deadline), transaction.responseUrl,
                    transaction.redirectUrl == null ? "" : transaction.redirectUrl,
                    "/api/v1/esign/auth?txnref=" + URLEncoder.encode(txnref, StandardCharsets.UTF_8),
                    transaction.callbackDelivered, transaction.callbackError,
                    transaction.certificateBase64 != null && !transaction.certificateBase64.isBlank(),
                    transaction.documents.stream().map(document -> DocumentView.of(document, transaction)).toList());
        }
    }

    public record DocumentView(String id, String docInfo, String docUrl, String hashHex, String responseSigType,
                               String error, String signaturePreview) {
        static DocumentView of(EsignTransaction.DocInput document, EsignTransaction transaction) {
            EsignTransaction.DocOutput result = transaction.results.stream()
                    .filter(item -> item.id().equals(document.id()))
                    .findFirst()
                    .orElse(null);
            String signature = result == null ? "" : result.signatureBase64();
            String preview = signature.length() <= 48 ? signature : signature.substring(0, 48) + "…";
            return new DocumentView(document.id(), document.docInfo(), document.docUrl(), document.hashHex(),
                    document.responseSigType(), result == null ? "" : result.error(), preview);
        }
    }
}
