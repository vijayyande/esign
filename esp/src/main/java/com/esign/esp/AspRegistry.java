package com.esign.esp;

import com.esign.common.audit.AuditLog;
import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.hash.Hashes;
import com.esign.common.time.IstTimestamps;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class AspRegistry {
    private static final Pattern ASP_ID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final EspProperties properties;
    private final AuditLog auditLog;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, OnboardedAsp> asps = new ConcurrentHashMap<>();

    public AspRegistry(EspProperties properties, AuditLog auditLog, ObjectMapper json) {
        this.properties = properties;
        this.auditLog = auditLog;
        this.json = json;
        load();
    }

    public List<AspView> list() {
        return asps.values().stream()
                .sorted(Comparator.comparing(OnboardedAsp::aspId))
                .map(AspView::of)
                .toList();
    }

    public List<String> ids() {
        return asps.keySet().stream().sorted().toList();
    }

    public AspView onboard(String aspId, String name, String certificatePem) {
        OnboardedAsp stored = remember(aspId, name, certificatePem);
        auditLog.add("ASP_ONBOARDED", stored.aspId, stored.fingerprint);
        return AspView.of(stored);
    }

    public AspView onboardFromUrl(String aspId, String name, String certificateUrl) {
        URI uri = parseUrl(certificateUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT,
                        "Certificate URL returned HTTP " + response.statusCode());
            }
            return onboard(aspId, name, response.body());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Unable to fetch the ASP certificate");
        }
    }

    public void revoke(String aspId) {
        if (asps.remove(aspId) == null) {
            throw new ApiException(EsignErrorCode.INVALID_ASP_ID);
        }
        save();
        auditLog.add("ASP_REVOKED", aspId, "");
    }

    public void require(String aspId, X509Certificate certificate, EsignErrorCode unknown, EsignErrorCode mismatch) {
        OnboardedAsp onboarded = asps.get(aspId);
        if (onboarded == null) {
            throw new ApiException(unknown, "ASP is not onboarded");
        }
        if (!onboarded.fingerprint.equals(fingerprint(certificate))) {
            throw new ApiException(mismatch, "Certificate is not the one onboarded for this ASP");
        }
    }

    private OnboardedAsp remember(String aspId, String name, String certificatePem) {
        if (aspId == null || !ASP_ID.matcher(aspId.trim()).matches()) {
            throw new ApiException(EsignErrorCode.INVALID_ASP_ID);
        }
        if (name == null || name.isBlank() || name.trim().length() > 80) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "ASP name is required and must be at most 80 characters");
        }
        X509Certificate certificate = parsePem(certificatePem);
        try {
            certificate.checkValidity();
        } catch (java.security.cert.CertificateException exception) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Certificate is expired or not yet valid");
        }
        OnboardedAsp stored = new OnboardedAsp(
                aspId.trim(),
                name.trim(),
                DocumentSignatures.pem(certificate),
                certificate.getSubjectX500Principal().getName(),
                fingerprint(certificate),
                certificate.getNotAfter().toInstant().toString(),
                IstTimestamps.now());
        asps.put(stored.aspId, stored);
        save();
        return stored;
    }

    private void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<StoredAsp> stored = json.readValue(path.toFile(), new TypeReference<>() {
            });
            for (StoredAsp item : stored) {
                remember(item.aspId, item.name, item.certificatePem);
            }
        } catch (ApiException exception) {
            throw new IllegalStateException("Stored ASP registry is not valid", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the ASP registry", exception);
        }
    }

    private void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            List<StoredAsp> stored = new ArrayList<>();
            for (OnboardedAsp asp : asps.values()) {
                stored.add(new StoredAsp(asp.aspId, asp.name, asp.certificatePem));
            }
            stored.sort(Comparator.comparing(item -> item.aspId));
            json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), stored);
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, "Unable to store the ASP registry");
        }
    }

    private Path file() {
        return Path.of(properties.getDataDir(), "asps.json");
    }

    private static URI parseUrl(String certificateUrl) {
        if (certificateUrl == null || certificateUrl.isBlank()) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Certificate URL is required");
        }
        try {
            URI uri = URI.create(certificateUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Certificate URL must be http or https");
            }
            return uri;
        } catch (ApiException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Certificate URL is not valid");
        }
    }

    static X509Certificate parsePem(String pem) {
        if (pem == null || !pem.contains("BEGIN CERTIFICATE")) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Paste a PEM certificate");
        }
        try {
            String body = pem.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Certificate PEM could not be read");
        }
    }

    private static String fingerprint(X509Certificate certificate) {
        return HexFormat.of().withUpperCase().formatHex(Hashes.sha256(DocumentSignatures.encoded(certificate)));
    }

    private record StoredAsp(String aspId, String name, String certificatePem) {
    }

    private record OnboardedAsp(String aspId, String name, String certificatePem, String subject, String fingerprint,
                                String notAfter, String onboardedAt) {
    }

    public record AspView(String aspId, String name, String subject, String fingerprint, String notAfter,
                          String onboardedAt, String certificatePem) {
        static AspView of(OnboardedAsp asp) {
            return new AspView(asp.aspId, asp.name, asp.subject, asp.fingerprint, asp.notAfter, asp.onboardedAt,
                    asp.certificatePem);
        }
    }
}
