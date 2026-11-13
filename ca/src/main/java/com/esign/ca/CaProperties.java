package com.esign.ca;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "esign")
public class CaProperties {
    private String dataDir = "data/ca";
    private String keystorePassword = "changeit";
    private int certificateValidityHours = 24;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public int getCertificateValidityHours() {
        return certificateValidityHours;
    }

    public void setCertificateValidityHours(int certificateValidityHours) {
        this.certificateValidityHours = certificateValidityHours;
    }
}
