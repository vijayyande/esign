package com.esign.esp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "esign")
public class EspProperties {
    private String espId = "ESP001";
    private List<String> allowedAspIds = new ArrayList<>(List.of("ASP001"));
    private String ekycUrl = "http://localhost:8083/api/v1/ekyc";
    private String otpUrl = "http://localhost:8083/api/v1/otp";
    private String caUrl = "http://localhost:8084/api/v1/certificates";
    private String dataDir = "data/esp";

    public String getEspId() {
        return espId;
    }

    public void setEspId(String espId) {
        this.espId = espId;
    }

    public List<String> getAllowedAspIds() {
        return allowedAspIds;
    }

    public void setAllowedAspIds(List<String> allowedAspIds) {
        this.allowedAspIds = allowedAspIds;
    }

    public String getEkycUrl() {
        return ekycUrl;
    }

    public void setEkycUrl(String ekycUrl) {
        this.ekycUrl = ekycUrl;
    }

    public String getOtpUrl() {
        return otpUrl;
    }

    public void setOtpUrl(String otpUrl) {
        this.otpUrl = otpUrl;
    }

    public String getCaUrl() {
        return caUrl;
    }

    public void setCaUrl(String caUrl) {
        this.caUrl = caUrl;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }
}
