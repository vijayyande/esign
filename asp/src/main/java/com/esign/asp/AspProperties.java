package com.esign.asp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "esign")
public class AspProperties {
    private String aspId = "ASP001";
    private String publicBaseUrl = "http://localhost:8081";
    private String espSignUrl = "http://localhost:8082/api/v1/esign";
    private String espStatusUrl = "http://localhost:8082/api/v1/esign/status";
    private String espAuthUrl = "http://localhost:8082/api/v1/esign/auth";
    private String dataDir = "data/asp";
    private String keystorePassword = "changeit";

    public String getAspId() {
        return aspId;
    }

    public void setAspId(String aspId) {
        this.aspId = aspId;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getEspSignUrl() {
        return espSignUrl;
    }

    public void setEspSignUrl(String espSignUrl) {
        this.espSignUrl = espSignUrl;
    }

    public String getEspStatusUrl() {
        return espStatusUrl;
    }

    public void setEspStatusUrl(String espStatusUrl) {
        this.espStatusUrl = espStatusUrl;
    }

    public String getEspAuthUrl() {
        return espAuthUrl;
    }

    public void setEspAuthUrl(String espAuthUrl) {
        this.espAuthUrl = espAuthUrl;
    }

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
}
