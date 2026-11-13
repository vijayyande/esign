package com.esign.asp;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HexFormat;
import java.util.Map;

/**
 * Builds a PDF with an empty CMS signature placeholder and later writes the eSign signature into it.
 * The hash sent to the ESP is SHA-256 of the PDF byte ranges, which exclude the placeholder.
 */
public final class SignedPdf {
    static final int PLACEHOLDER_BYTES = 16 * 1024;

    private SignedPdf() {
    }

    public static boolean embeds(String responseSigType) {
        return "PKCS7".equals(responseSigType) || "PKCS7pdf".equals(responseSigType) || "PKCS7complete".equals(responseSigType);
    }

    public record Slot(byte[] pdf, int hexStart, int hexLength, String hashHex) {
    }

    /**
     * Visible signature box on the rendered page. Origin is the top-left of the page as shown,
     * and the units are PDF points.
     */
    public record Placement(Integer page, Float x, Float y, Float width, Float height) {
    }

    public record PageSize(int page, float width, float height, int rotation) {
    }

    public static Slot prepare(byte[] original, String fileName, String docInfo, String signerId) throws IOException {
        return prepare(original, fileName, docInfo, signerId, null);
    }

    public static Slot prepare(byte[] original, String fileName, String docInfo, String signerId, Placement placement)
            throws IOException {
        Path directory = Files.createTempDirectory("esign-pdf");
        Path base = directory.resolve("base.pdf");
        Path prepared = directory.resolve("prepared.pdf");
        try {
            writeBase(original, fileName, docInfo, signerId, base);
            try (PDDocument document = PDDocument.load(base.toFile())) {
                int pageIndex = pageIndex(document, placement == null ? null : placement.page());
                PDPage page = document.getPage(pageIndex);
                PDRectangle rect = userRectangle(page, placement);
                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName(ascii(signerId, 80));
                signature.setReason("CCA eSign API 3.3");
                signature.setLocation("India");
                signature.setSignDate(istNow());
                try (SignatureOptions options = new SignatureOptions()) {
                    options.setPreferredSignatureSize(PLACEHOLDER_BYTES);
                    options.setPage(pageIndex);
                    options.setVisualSignature(visualTemplate(document, pageIndex, rect, signerId));
                    document.addSignature(signature, options);
                    try (OutputStream output = Files.newOutputStream(prepared)) {
                        var external = document.saveIncrementalForExternalSigning(output);
                        external.getContent().readAllBytes();
                        external.setSignature(new byte[] {0});
                    }
                }
            }
            byte[] pdf = Files.readAllBytes(prepared);
            int[] range = byteRange(pdf);
            int hexStart = placeholderStart(pdf, range);
            int hexLength = range[2] - hexStart - 1;
            if (hexLength < 64 || pdf[hexStart - 1] != '<' || pdf[hexStart + hexLength] != '>') {
                throw new IOException("Signature placeholder was not reserved");
            }
            return new Slot(pdf, hexStart, hexLength, HexFormat.of().formatHex(digest(pdf, range)));
        } finally {
            Files.deleteIfExists(prepared);
            Files.deleteIfExists(base);
            Files.deleteIfExists(directory);
        }
    }

    public static byte[] embed(byte[] prepared, int hexStart, int hexLength, byte[] cms) {
        byte[] hex = HexFormat.of().formatHex(cms).getBytes(StandardCharsets.US_ASCII);
        if (hex.length > hexLength) {
            throw new IllegalArgumentException("CMS signature does not fit the PDF placeholder");
        }
        byte[] signed = prepared.clone();
        System.arraycopy(hex, 0, signed, hexStart, hex.length);
        return signed;
    }

    public static byte[] digest(byte[] pdf) throws IOException {
        return digest(pdf, byteRange(pdf));
    }

    public static java.util.List<PageSize> pages(byte[] original, String fileName, String docInfo, String signerId)
            throws IOException {
        try (PDDocument document = PDDocument.load(baseBytes(original, fileName, docInfo, signerId))) {
            java.util.List<PageSize> pages = new ArrayList<>();
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                PDPage page = document.getPage(index);
                int rotation = normalizedRotation(page);
                PDRectangle crop = page.getCropBox();
                boolean swapped = rotation == 90 || rotation == 270;
                pages.add(new PageSize(index + 1, swapped ? crop.getHeight() : crop.getWidth(),
                        swapped ? crop.getWidth() : crop.getHeight(), rotation));
            }
            return pages;
        }
    }

    public static byte[] preview(byte[] original, String fileName, String docInfo, String signerId, int page)
            throws IOException {
        try (PDDocument document = PDDocument.load(baseBytes(original, fileName, docInfo, signerId))) {
            int index = pageIndex(document, page);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(index, 110, ImageType.RGB), "PNG", output);
            return output.toByteArray();
        }
    }

    private static byte[] baseBytes(byte[] original, String fileName, String docInfo, String signerId) throws IOException {
        Path directory = Files.createTempDirectory("esign-base");
        Path base = directory.resolve("base.pdf");
        try {
            writeBase(original, fileName, docInfo, signerId, base);
            return Files.readAllBytes(base);
        } finally {
            Files.deleteIfExists(base);
            Files.deleteIfExists(directory);
        }
    }

    private static int pageIndex(PDDocument document, Integer page) {
        int count = document.getNumberOfPages();
        if (page == null || page < 1 || page > count) {
            return count - 1;
        }
        return page - 1;
    }

    private static PDRectangle userRectangle(PDPage page, Placement placement) {
        int rotation = normalizedRotation(page);
        PDRectangle crop = page.getCropBox();
        boolean swapped = rotation == 90 || rotation == 270;
        float shownWidth = swapped ? crop.getHeight() : crop.getWidth();
        float shownHeight = swapped ? crop.getWidth() : crop.getHeight();
        float width = clamp(placement == null || placement.width() == null ? 190 : placement.width(), 72, shownWidth);
        float height = clamp(placement == null || placement.height() == null ? 64 : placement.height(), 36, shownHeight);
        float fromLeft = clamp(placement == null || placement.x() == null ? 36 : placement.x(), 0, shownWidth - width);
        float fromTop = placement == null || placement.y() == null ? shownHeight - height - 36 : placement.y();
        fromTop = clamp(fromTop, 0, shownHeight - height);
        float left = crop.getLowerLeftX();
        float bottom = crop.getLowerLeftY();
        return switch (rotation) {
            case 90 -> new PDRectangle(left + fromTop, bottom + crop.getHeight() - fromLeft - width, height, width);
            case 180 -> new PDRectangle(left + crop.getWidth() - fromLeft - width, bottom + fromTop, width, height);
            case 270 -> new PDRectangle(left + crop.getWidth() - fromTop - height, bottom + fromLeft, height, width);
            default -> new PDRectangle(left + fromLeft, bottom + crop.getHeight() - fromTop - height, width, height);
        };
    }

    private static int normalizedRotation(PDPage page) {
        int rotation = page.getRotation() % 360;
        return rotation < 0 ? rotation + 360 : rotation;
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static InputStream visualTemplate(PDDocument source, int pageIndex, PDRectangle rect, String signerId)
            throws IOException {
        try (PDDocument visual = new PDDocument()) {
            PDPage page = new PDPage(source.getPage(pageIndex).getMediaBox());
            visual.addPage(page);
            PDAcroForm form = new PDAcroForm(visual);
            visual.getDocumentCatalog().setAcroForm(form);
            PDSignatureField field = new PDSignatureField(form);
            var widget = field.getWidgets().get(0);
            form.setFields(new ArrayList<>(java.util.List.of(field)));
            widget.setRectangle(rect);
            widget.setPage(page);
            page.getAnnotations().add(widget);
            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream stream = new PDAppearanceStream(visual);
            stream.setResources(new PDResources());
            stream.setBBox(new PDRectangle(rect.getWidth(), rect.getHeight()));
            appearance.setNormalAppearance(stream);
            widget.setAppearance(appearance);
            try (PDPageContentStream contents = new PDPageContentStream(visual, stream)) {
                float width = rect.getWidth();
                float height = rect.getHeight();
                contents.setNonStrokingColor(232, 243, 242);
                contents.addRect(0, 0, width, height);
                contents.fill();
                contents.setStrokingColor(14, 92, 90);
                contents.setLineWidth(1.2f);
                contents.addRect(1, 1, Math.max(1, width - 2), Math.max(1, height - 2));
                contents.stroke();
                contents.beginText();
                contents.setNonStrokingColor(20, 32, 51);
                contents.setFont(PDType1Font.HELVETICA_BOLD, 9);
                contents.newLineAtOffset(8, Math.max(8, height - 18));
                contents.showText("Digitally signed");
                if (height >= 36) {
                    contents.setFont(PDType1Font.HELVETICA, 8);
                    contents.newLineAtOffset(0, -12);
                    contents.showText(ascii(signerId, 42));
                }
                contents.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            visual.save(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private static void writeBase(byte[] original, String fileName, String docInfo, String signerId, Path base)
            throws IOException {
        if (isPdf(original)) {
            try (PDDocument probe = PDDocument.load(original)) {
                if (probe.getNumberOfPages() > 0) {
                    Files.write(base, original);
                    return;
                }
            } catch (IOException ignored) {
                // Fall through and wrap the bytes in a new PDF.
            }
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                stream.newLineAtOffset(48, 760);
                stream.showText("eSign document");
                stream.setFont(PDType1Font.HELVETICA, 11);
                stream.newLineAtOffset(0, -28);
                stream.showText("Information: " + ascii(docInfo, 90));
                stream.newLineAtOffset(0, -16);
                stream.showText("File: " + ascii(fileName, 90));
                stream.newLineAtOffset(0, -16);
                stream.showText("Signer: " + ascii(signerId, 90));
                stream.newLineAtOffset(0, -16);
                stream.showText("Prepared: " + ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                int y = 660;
                for (String line : previewLines(original)) {
                    y -= 14;
                    if (y < 120) {
                        break;
                    }
                    stream.newLineAtOffset(0, -14);
                    stream.showText(line);
                }
                stream.endText();
            }
            attach(document, original, fileName);
            document.save(base.toFile());
        }
    }

    private static void attach(PDDocument document, byte[] original, String fileName) throws IOException {
        String name = ascii(fileName == null || fileName.isBlank() ? "document.bin" : fileName, 80);
        PDEmbeddedFile embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(original));
        embedded.setSubtype("application/octet-stream");
        embedded.setSize(original.length);
        embedded.setCreationDate(istNow());
        PDComplexFileSpecification specification = new PDComplexFileSpecification();
        specification.setFile(name);
        specification.setEmbeddedFile(embedded);
        PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
        tree.setNames(Map.of(name, specification));
        PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
        names.setEmbeddedFiles(tree);
        document.getDocumentCatalog().setNames(names);
    }

    private static int[] byteRange(byte[] pdf) throws IOException {
        int at = last(pdf, "/ByteRange");
        if (at < 0) {
            throw new IOException("PDF ByteRange is missing");
        }
        int open = indexOf(pdf, '[', at);
        int close = indexOf(pdf, ']', open);
        if (open < 0 || close < 0) {
            throw new IOException("PDF ByteRange is malformed");
        }
        String[] parts = new String(pdf, open + 1, close - open - 1, StandardCharsets.US_ASCII).trim().split("\\s+");
        if (parts.length != 4) {
            throw new IOException("PDF ByteRange is malformed");
        }
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
        };
    }

    private static int placeholderStart(byte[] pdf, int[] range) throws IOException {
        int gapStart = range[0] + range[1];
        int gapEnd = range[2];
        for (int i = gapStart; i < gapEnd; i++) {
            if (pdf[i] == '<') {
                return i + 1;
            }
        }
        throw new IOException("Signature contents placeholder is missing");
    }

    private static byte[] digest(byte[] pdf, int[] range) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pdf, range[0], range[1]);
            digest.update(pdf, range[2], range[3]);
            return digest.digest();
        } catch (Exception exception) {
            throw new IOException("SHA-256 is not available", exception);
        }
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private static java.util.List<String> previewLines(byte[] original) {
        if (original.length == 0 || original.length > 20_000) {
            return java.util.List.of("Original file is attached to this PDF.");
        }
        for (byte value : original) {
            if (value == 0) {
                return java.util.List.of("Original file is attached to this PDF.");
            }
        }
        String text = new String(original, StandardCharsets.UTF_8);
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = ascii(raw, 90);
            if (!line.isBlank()) {
                lines.add(line);
            }
            if (lines.size() == 30) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return java.util.List.of("Original file is attached to this PDF.");
        }
        return lines;
    }

    private static Calendar istNow() {
        return GregorianCalendar.from(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")));
    }

    private static String ascii(String value, int max) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length() && builder.length() < max; i++) {
            char character = value.charAt(i);
            builder.append(character >= 32 && character < 127 ? character : ' ');
        }
        return builder.toString();
    }

    private static int last(byte[] pdf, String token) {
        byte[] needle = token.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = pdf.length - needle.length; i >= 0; i--) {
            for (int j = 0; j < needle.length; j++) {
                if (pdf[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int indexOf(byte[] pdf, char token, int from) {
        for (int i = Math.max(0, from); i < pdf.length; i++) {
            if (pdf[i] == token) {
                return i;
            }
        }
        return -1;
    }
}
