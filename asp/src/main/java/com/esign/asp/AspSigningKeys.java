package com.esign.asp;

import com.esign.common.crypto.KeyMaterial;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

final class AspSigningKeys {
    private AspSigningKeys() {
    }

    static KeyMaterial load(AspProperties properties) {
        try {
            Path directory = Path.of(properties.getDataDir());
            Files.createDirectories(directory);
            Path storePath = directory.resolve("asp.p12");
            char[] password = properties.getKeystorePassword().toCharArray();
            KeyStore store = KeyStore.getInstance("PKCS12");
            if (Files.exists(storePath)) {
                try (InputStream input = Files.newInputStream(storePath)) {
                    store.load(input, password);
                }
                PrivateKey key = (PrivateKey) store.getKey("asp", password);
                X509Certificate certificate = (X509Certificate) store.getCertificate("asp");
                return new KeyMaterial(key, certificate);
            }
            KeyMaterial created = KeyMaterial.generate(properties.getAspId());
            store.load(null, password);
            store.setKeyEntry("asp", created.privateKey(), password, new java.security.cert.Certificate[]{created.certificate()});
            try (OutputStream output = Files.newOutputStream(storePath)) {
                store.store(output, password);
            }
            return created;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the ASP signing key", exception);
        }
    }
}
