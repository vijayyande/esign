package com.esign.common;

import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.KeyMaterial;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.hash.Hashes;
import com.esign.common.validation.EsignXmlValidator;
import com.esign.common.validation.SignerIds;
import com.esign.common.xml.EsignRequest;
import com.esign.common.xml.XmlCodec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonContractTest {
    @Test
    void errorCatalogueHasEverySectionFiveCode() {
        assertEquals(69, EsignErrorCode.values().length);
        assertEquals("Invalid Request Format", EsignErrorCode.INVALID_REQUEST_FORMAT.message());
        assertEquals("521", EsignErrorCode.ORG_INVALID_PHYSICAL_VERIFICATION.code());
        assertEquals(EsignErrorCode.USER_TIMEOUT, EsignErrorCode.fromCode("113"));
    }

    @Test
    void pinHashUsesTxnPrefix() {
        String hash = Hashes.pinHash("txn-1", "123456");
        assertEquals(Hashes.sha256Hex("txn-1" + Hashes.sha256Hex("123456")), hash);
        assertTrue(Hashes.constantTimeEquals(hash, hash));
    }

    @Test
    void signerIdAcceptsTheThreeSpecTypes() {
        assertEquals("username", SignerIds.parse("demo.user@username.ESP001").idType());
        assertEquals("9876543210", SignerIds.parse("9876543210@Mobile.ESP001").id());
        assertEquals("ABCDE1234F", SignerIds.parse("ABCDE1234F@PAN.ESP001").id());
        assertEquals(null, SignerIds.parse("bad id"));
    }

    @Test
    void requestValidatorRejectsEmptyDocumentsAndAcceptsAValidRequest() {
        EsignXmlValidator validator = new EsignXmlValidator(Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), ZoneOffset.UTC));
        EsignRequest request = sample("2026-10-13T11:30:00+05:30");
        validator.validateRequest(request, "ASP001");
        request.docs.inputHashes.clear();
        ApiException exception = assertThrows(ApiException.class, () -> validator.validateRequest(request, "ASP001"));
        assertEquals(EsignErrorCode.DOCUMENT_REQUIRED, exception.errorCode());
    }

    @Test
    void xmlSignatureRoundTripAndTamperDetection() {
        EsignRequest request = sample("2026-10-13T11:30:00+05:30");
        XmlSigner signer = new XmlSigner(KeyMaterial.generate("ASP001"));
        String signed = signer.sign(XmlCodec.marshal(request));
        signer.verify(signed, EsignErrorCode.INVALID_DIGITAL_SIGNATURE, EsignErrorCode.XML_SIGNATURE_FAILED);
        EsignRequest parsed = XmlCodec.unmarshal(signed, EsignRequest.class, EsignErrorCode.INVALID_REQUEST_FORMAT);
        assertEquals("txn-1", parsed.txn);
        assertThrows(ApiException.class, () -> signer.verify(
                signed.replace("txn-1", "txn-2"),
                EsignErrorCode.INVALID_DIGITAL_SIGNATURE,
                EsignErrorCode.XML_SIGNATURE_FAILED));
    }

    @Test
    void rawAndPkcs7SignaturesVerifyAgainstTheDocumentHash() {
        byte[] hash = Hashes.sha256("hello".getBytes());
        KeyMaterial key = KeyMaterial.generate("signer");
        for (String type : List.of("raw", "PKCS7", "PKCS7pdf", "PKCS7complete")) {
            byte[] signature = DocumentSignatures.sign(hash, key.privateKey(), key.certificate(), List.of(key.certificate()), type);
            assertTrue(DocumentSignatures.verifies(hash, signature, key.certificate().getPublicKey(), type), type);
        }
        assertEquals(64, HexFormat.of().formatHex(hash).length());
    }

    private static EsignRequest sample(String timestamp) {
        EsignRequest request = new EsignRequest();
        request.ver = "3.3";
        request.signerid = "demo.user@username.ESP001";
        request.ts = timestamp;
        request.txn = "txn-1";
        request.maxWaitPeriod = "30";
        request.aspId = "ASP001";
        request.responseUrl = "http://localhost:8081/api/v1/esign/callback";
        request.redirectUrl = "http://localhost:8081/api/v1/esign/return";
        request.signingAlgorithm = "RSA";
        request.docs = new EsignRequest.Docs();
        EsignRequest.InputHash hash = new EsignRequest.InputHash();
        hash.id = "1";
        hash.hashAlgorithm = "SHA256";
        hash.docInfo = "Sample";
        hash.docUrl = "http://localhost:8081/api/v1/documents/1/content";
        hash.responseSigType = "raw";
        hash.value = "a".repeat(64);
        request.docs.inputHashes.add(hash);
        return request;
    }
}
