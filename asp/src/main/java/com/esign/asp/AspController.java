package com.esign.asp;

import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AspController {
    private final AspEsignService service;
    private final XmlSigner xmlSigner;

    public AspController(AspEsignService service, XmlSigner xmlSigner) {
        this.service = service;
        this.xmlSigner = xmlSigner;
    }

    @GetMapping("/meta")
    public Map<String, String> meta() {
        return Map.of("component", "ASP", "specVersion", "3.3", "port", "8081");
    }

    @GetMapping(value = "/meta/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String certificate() {
        return DocumentSignatures.pem(xmlSigner.certificate());
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@RequestParam("file") MultipartFile file, @RequestParam String docInfo) {
        return DocumentView.of(service.store(file, docInfo));
    }

    @GetMapping("/documents")
    public List<DocumentView> documents() {
        return service.documents().stream().map(DocumentView::of).toList();
    }

    @GetMapping("/documents/{id}/pages")
    public java.util.List<SignedPdf.PageSize> pages(@PathVariable String id) {
        return service.pages(id);
    }

    @GetMapping(value = "/documents/{id}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] preview(@PathVariable String id, @RequestParam(defaultValue = "1") int page) {
        return service.preview(id, page);
    }

    @GetMapping("/documents/{id}/content")
    public ResponseEntity<FileSystemResource> content(@PathVariable String id) {
        AspEsignService.StoredDocument document = service.document(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.fileName.replace("\"", "") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(document.path));
    }

    @GetMapping("/documents/{id}/signature")
    public Map<String, Object> signature(@PathVariable String id) {
        AspEsignService.StoredDocument document = service.document(id);
        return Map.of(
                "documentId", document.id,
                "hashHex", document.hashHex,
                "responseSigType", document.responseSigType,
                "signatureBase64", document.signatureBase64 == null ? "" : document.signatureBase64,
                "certificateBase64", document.certificateBase64 == null ? "" : document.certificateBase64,
                "verified", document.verified,
                "error", document.signatureError,
                "pdfError", document.pdfError,
                "signedPdf", document.signedPath == null ? "" : "/api/v1/documents/" + document.id + "/signed.pdf");
    }

    @GetMapping(value = "/documents/{id}/prepared", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<FileSystemResource> prepared(@PathVariable String id) {
        AspEsignService.StoredDocument document = service.document(id);
        if (document.preparedPath == null) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_URL);
        }
        return pdf(document.preparedPath, "prepared.pdf");
    }

    @GetMapping(value = "/documents/{id}/signed.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<FileSystemResource> signed(@PathVariable String id) {
        AspEsignService.StoredDocument document = service.document(id);
        if (document.signedPath == null) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_URL);
        }
        return pdf(document.signedPath, "signed.pdf");
    }

    private static ResponseEntity<FileSystemResource> pdf(Path path, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(path));
    }

    @PostMapping("/esign/requests")
    public AspEsignService.CreatedRequest create(@RequestBody AspEsignService.SignCommand command) {
        return service.create(command);
    }

    @PostMapping(value = "/esign/callback", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> callback(@RequestBody String xml) {
        service.callback(xml);
        return ResponseEntity.ok("<CallbackAck status=\"1\"/>");
    }

    @PostMapping(value = "/esign/return", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String finish(@RequestParam String txnref) {
        return service.returnPage(txnref);
    }

    @GetMapping("/esign/transactions/{txn}")
    public AspEsignService.SigningTransaction transaction(@PathVariable String txn) {
        return service.require(txn);
    }

    @PostMapping("/esign/transactions/{txn}/status")
    public AspEsignService.SigningTransaction status(@PathVariable String txn) {
        return service.refresh(txn);
    }

    @GetMapping("/esign/transactions")
    public List<AspEsignService.SigningTransaction> transactions() {
        return service.transactions();
    }

    public record DocumentView(String documentId, String fileName, String docInfo, String hashHex, String docUrl) {
        static DocumentView of(AspEsignService.StoredDocument document) {
            return new DocumentView(document.id, document.fileName, document.docInfo, document.hashHex,
                    "/api/v1/documents/" + document.id + "/content");
        }
    }
}
