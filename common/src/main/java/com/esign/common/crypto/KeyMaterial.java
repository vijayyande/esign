package com.esign.common.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public record KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static KeyMaterial generate(String commonName) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            X500Name name = new X500Name("CN=" + escape(commonName) + ",O=eSign Reference,C=IN");
            Instant now = Instant.now();
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(pair.getPrivate());
            X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
                    name,
                    new BigInteger(64, new SecureRandom()),
                    Date.from(now.minus(5, ChronoUnit.MINUTES)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)),
                    name,
                    pair.getPublic()).build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);
            return new KeyMaterial(pair.getPrivate(), certificate);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate XML signing key", exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,");
    }
}
