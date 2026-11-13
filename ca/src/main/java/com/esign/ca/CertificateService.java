package com.esign.ca;

import com.esign.common.audit.AuditLog;
import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.KeyMaterial;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

@Service
public class CertificateService {
    private final CaProperties properties;
    private final AuditLog auditLog;
    private final PrivateKey caKey;
    private final X509Certificate caCertificate;
    private final SecureRandom random = new SecureRandom();

    public CertificateService(CaProperties properties, AuditLog auditLog) {
        this.properties = properties;
        this.auditLog = auditLog;
        Loaded loaded = loadOrCreate();
        this.caKey = loaded.key();
        this.caCertificate = loaded.certificate();
    }

    public X509Certificate caCertificate() {
        return caCertificate;
    }

    public Issued issue(IssueRequest request) {
        if (request == null || request.subjectName() == null || request.subjectName().isBlank()) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "Subject name is required");
        }
        if (request.documents() == null || request.documents().isEmpty()) {
            throw new ApiException(EsignErrorCode.DOCUMENT_REQUIRED);
        }
        boolean ecdsa = "ECDSA".equals(request.signingAlgorithm());
        if (!ecdsa && !"RSA".equals(request.signingAlgorithm())) {
            throw new ApiException(EsignErrorCode.INVALID_REQUEST_FORMAT, "signingAlgorithm must be RSA or ECDSA");
        }
        try {
            KeyPair pair = generateKeyPair(ecdsa);
            X509Certificate certificate = issueCertificate(request.subjectName(), pair);
            List<IssuedSignature> signatures = request.documents().stream()
                    .map(document -> signOne(document, pair.getPrivate(), certificate))
                    .toList();
            auditLog.add("CERTIFICATE_ISSUED", certificate.getSerialNumber().toString(16), request.subjectName());
            return new Issued(
                    Base64.getEncoder().encodeToString(certificate.getEncoded()),
                    DocumentSignatures.pem(certificate),
                    Base64.getEncoder().encodeToString(caCertificate.getEncoded()),
                    signatures);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }

    private IssuedSignature signOne(DocumentToSign document, PrivateKey privateKey, X509Certificate certificate) {
        try {
            byte[] hash = HexFormat.of().parseHex(document.hashHex());
            DocumentSignatures.requireSha256(hash);
            byte[] signature = DocumentSignatures.sign(
                    hash, privateKey, certificate, List.of(caCertificate), document.responseSigType());
            return new IssuedSignature(document.id(), Base64.getEncoder().encodeToString(signature), "SHA256", "");
        } catch (ApiException exception) {
            return new IssuedSignature(document.id(), "", "SHA256", exception.errorCode().code());
        } catch (RuntimeException exception) {
            return new IssuedSignature(document.id(), "", "SHA256", EsignErrorCode.INVALID_DOCUMENT_HASH.code());
        }
    }

    private X509Certificate issueCertificate(String subjectName, KeyPair pair) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, subjectName)
                .addRDN(BCStyle.OU, "eSign")
                .addRDN(BCStyle.O, "eSign Reference CA")
                .addRDN(BCStyle.C, "IN")
                .build();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caCertificate,
                new BigInteger(128, random).abs().add(BigInteger.ONE),
                Date.from(now.minus(5, ChronoUnit.MINUTES)),
                Date.from(now.plus(properties.getCertificateValidityHours(), ChronoUnit.HOURS)),
                subject,
                pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(caKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);
    }

    private static KeyPair generateKeyPair(boolean ecdsa) throws Exception {
        if (ecdsa) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Loaded loadOrCreate() {
        try {
            Path directory = Path.of(properties.getDataDir());
            Files.createDirectories(directory);
            Path storePath = directory.resolve("ca.p12");
            char[] password = properties.getKeystorePassword().toCharArray();
            KeyStore store = KeyStore.getInstance("PKCS12");
            if (Files.exists(storePath)) {
                try (InputStream input = Files.newInputStream(storePath)) {
                    store.load(input, password);
                }
                PrivateKey key = (PrivateKey) store.getKey("ca", password);
                X509Certificate certificate = (X509Certificate) store.getCertificate("ca");
                return new Loaded(key, certificate);
            }
            KeyMaterial material = KeyMaterial.generate("eSign Reference CA");
            X509Certificate certificate = strengthenCaCertificate(material);
            store.load(null, password);
            store.setKeyEntry("ca", material.privateKey(), password, new java.security.cert.Certificate[]{certificate});
            try (OutputStream output = Files.newOutputStream(storePath)) {
                store.store(output, password);
            }
            return new Loaded(material.privateKey(), certificate);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the CA key", exception);
        }
    }

    private static X509Certificate strengthenCaCertificate(KeyMaterial material) throws Exception {
        Instant now = Instant.now();
        X500Name name = new X500Name("CN=eSign Reference CA,O=eSign Reference,C=IN");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                new BigInteger(64, new SecureRandom()),
                Date.from(now.minus(5, ChronoUnit.MINUTES)),
                Date.from(now.plus(3650, ChronoUnit.DAYS)),
                name,
                material.certificate().getPublicKey());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(material.privateKey());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private record Loaded(PrivateKey key, X509Certificate certificate) {
    }

    public record IssueRequest(String subjectName, String signingAlgorithm, List<DocumentToSign> documents) {
    }

    public record DocumentToSign(String id, String hashHex, String responseSigType) {
    }

    public record Issued(String certificateBase64, String certificatePem, String caCertificateBase64,
                         List<IssuedSignature> signatures) {
    }

    public record IssuedSignature(String id, String signatureBase64, String sigHashAlgorithm, String error) {
    }
}
