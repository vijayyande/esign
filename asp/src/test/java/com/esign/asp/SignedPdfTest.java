package com.esign.asp;

import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.KeyMaterial;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedPdfTest {
    @Test
    void textUploadBecomesAVerifiableSignedPdf() throws Exception {
        byte[] original = "eSign API 3.3 demo document".getBytes(StandardCharsets.UTF_8);
        assertSigned(original, "note.txt", "PKCS7pdf");
    }

    @Test
    void existingPdfReceivesAnIncrementalSignature() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            document.save(output);
            assertSigned(output.toByteArray(), "already.pdf", "PKCS7");
        }
    }

    @Test
    void visualSignatureSitsWhereRequested() throws Exception {
        byte[] original = "Place the signature on this page.".getBytes(StandardCharsets.UTF_8);
        SignedPdf.Placement placement = new SignedPdf.Placement(1, 72f, 140f, 180f, 56f);
        SignedPdf.Slot slot = SignedPdf.prepare(original, "note.txt", "Demo document", "demo.user@username.ESP001", placement);
        KeyMaterial keys = KeyMaterial.generate("Demo Signer");
        byte[] cms = DocumentSignatures.sign(
                HexFormat.of().parseHex(slot.hashHex()), keys.privateKey(), keys.certificate(),
                List.of(keys.certificate()), "PKCS7pdf");
        byte[] signed = SignedPdf.embed(slot.pdf(), slot.hexStart(), slot.hexLength(), cms);
        try (PDDocument document = PDDocument.load(signed)) {
            PDSignatureField field = (PDSignatureField) document.getDocumentCatalog().getAcroForm().getFields().get(0);
            PDRectangle rect = field.getWidgets().get(0).getRectangle();
            PDRectangle crop = document.getPage(0).getCropBox();
            assertEquals(72, rect.getLowerLeftX(), 1.5);
            assertEquals(crop.getHeight() - 140 - 56, rect.getLowerLeftY(), 1.5);
            assertEquals(180, rect.getWidth(), 1.5);
            assertEquals(56, rect.getHeight(), 1.5);
        }
    }

    private static void assertSigned(byte[] original, String fileName, String sigType) throws Exception {
        SignedPdf.Slot slot = SignedPdf.prepare(original, fileName, "Demo document", "demo.user@username.ESP001");
        KeyMaterial keys = KeyMaterial.generate("Demo Signer");
        byte[] cms = DocumentSignatures.sign(
                HexFormat.of().parseHex(slot.hashHex()), keys.privateKey(), keys.certificate(),
                List.of(keys.certificate()), sigType);
        byte[] signed = SignedPdf.embed(slot.pdf(), slot.hexStart(), slot.hexLength(), cms);
        assertTrue(new String(signed, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(DocumentSignatures.verifies(
                HexFormat.of().parseHex(slot.hashHex()), cms, keys.certificate().getPublicKey(), sigType));
        assertTrue(Arrays.equals(SignedPdf.digest(signed), HexFormat.of().parseHex(slot.hashHex())));
        try (PDDocument document = PDDocument.load(signed)) {
            assertEquals(1, document.getSignatureDictionaries().size());
        }
    }
}
