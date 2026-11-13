package com.esign.ekyc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "esign")
public class EkycProperties {
    private boolean exposeDemoOtp = true;
    private boolean seedDemoUser = true;

    public boolean isExposeDemoOtp() {
        return exposeDemoOtp;
    }

    public void setExposeDemoOtp(boolean exposeDemoOtp) {
        this.exposeDemoOtp = exposeDemoOtp;
    }

    public boolean isSeedDemoUser() {
        return seedDemoUser;
    }

    public void setSeedDemoUser(boolean seedDemoUser) {
        this.seedDemoUser = seedDemoUser;
    }
}
