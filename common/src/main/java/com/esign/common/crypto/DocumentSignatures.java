package com.esign.common.crypto;

import com.esign.common.SpecTexts;
import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Signs the SHA-256 document hash from an eSign {@code InputHash}.
 * {@code raw} is PKCS#1 DigestInfo for RSA, or raw ECDSA over the hash.
 * PKCS7 forms are detached CMS signatures whose message-digest attribute is that hash.
 * PKCS7pdf and PKCS7complete include the issuer chain. This reference CA has no OCSP or CRL responder,
 * so revocation archival attributes are not embedded.
 */
public final class DocumentSignatures {
    private static final byte[] SHA256_DIGEST_INFO_PREFIX = new byte[]{
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01,
            0x05, 0x00, 0x04, 0x20
    };

    private DocumentSignatures() {
    }

    public static byte[] sign(byte[] sha256,
                              PrivateKey privateKey,
                              X509Certificate signer,
                              List<X509Certificate> chain,
                              String responseSigType) {
        try {
            return switch (responseSigType) {
                case "raw" -> raw(sha256, privateKey);
                case "PKCS7" -> cms(sha256, privateKey, signer, List.of());
                case "PKCS7pdf", "PKCS7complete" -> cms(sha256, privateKey, signer, chain);
                default -> throw new ApiException(EsignErrorCode.INVALID_RESPONSE_SIG_TYPE);
            };
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_DOCUMENT, exception.getMessage());
        }
    }

    public static boolean verifies(byte[] sha256, byte[] signature, PublicKey publicKey, String responseSigType) {
        try {
            if ("raw".equals(responseSigType)) {
                Signature verifier = Signature.getInstance(publicKey.getAlgorithm().startsWith("EC")
                        ? "NONEwithECDSA" : "NONEwithRSA");
                verifier.initVerify(publicKey);
                verifier.update(publicKey.getAlgorithm().startsWith("EC") ? sha256 : digestInfo(sha256));
                return verifier.verify(signature);
            }
            CMSSignedData cms = new CMSSignedData(signature);
            SignerInformation information = cms.getSignerInfos().iterator().next();
            var digestAttribute = information.getSignedAttributes().get(org.bouncycastle.asn1.cms.CMSAttributes.messageDigest);
            byte[] embedded = org.bouncycastle.asn1.ASN1OctetString.getInstance(
                    digestAttribute.getAttrValues().getObjectAt(0)).getOctets();
            if (!java.security.MessageDigest.isEqual(embedded, sha256)) {
                return false;
            }
            String algorithm = publicKey.getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(information.getEncodedSignedAttributes());
            return verifier.verify(information.getSignature());
        } catch (Exception exception) {
            return false;
        }
    }

    public static String pem(X509Certificate certificate) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded(certificate));
        return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    }

    public static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] raw(byte[] sha256, PrivateKey privateKey) throws Exception {
        boolean ec = privateKey.getAlgorithm().startsWith("EC");
        Signature signature = Signature.getInstance(ec ? "NONEwithECDSA" : "NONEwithRSA");
        signature.initSign(privateKey);
        signature.update(ec ? sha256 : digestInfo(sha256));
        return signature.sign();
    }

    private static byte[] digestInfo(byte[] sha256) {
        byte[] value = new byte[SHA256_DIGEST_INFO_PREFIX.length + sha256.length];
        System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, value, 0, SHA256_DIGEST_INFO_PREFIX.length);
        System.arraycopy(sha256, 0, value, SHA256_DIGEST_INFO_PREFIX.length, sha256.length);
        return value;
    }

    private static byte[] cms(byte[] sha256,
                              PrivateKey privateKey,
                              X509Certificate signer,
                              List<X509Certificate> chain) throws Exception {
        String algorithm = privateKey.getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
        DigestCalculatorProvider digests = algorithmIdentifier -> new FixedDigestCalculator(sha256);
        var contentSigner = new JcaContentSignerBuilder(algorithm)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey);
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(digests)
                .build(contentSigner, signer));
        List<X509Certificate> certificates = new ArrayList<>();
        certificates.add(signer);
        certificates.addAll(chain);
        generator.addCertificates(new JcaCertStore(certificates));
        CMSSignedData signed = generator.generate(new CMSProcessableByteArray(new byte[0]), false);
        return signed.getEncoded();
    }

    public static void requireSha256(byte[] hash) {
        if (hash == null || hash.length != 32) {
            throw new ApiException(EsignErrorCode.INVALID_DOCUMENT_HASH);
        }
    }

    public static String hashAlgorithm() {
        return SpecTexts.HASH_ALGORITHM;
    }

    private static final class FixedDigestCalculator implements DigestCalculator {
        private final byte[] digest;

        private FixedDigestCalculator(byte[] digest) {
            this.digest = digest.clone();
        }

        @Override
        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public byte[] getDigest() {
            return digest.clone();
        }
    }
}
