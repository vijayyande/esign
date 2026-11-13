package com.esign.ekyc;

import com.esign.common.audit.AuditLog;
import com.esign.common.crypto.KeyMaterial;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.http.XmlHttp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.esign.ekyc", "com.esign.common"})
@EnableConfigurationProperties(EkycProperties.class)
public class EkycApplication {
    public static void main(String[] args) {
        SpringApplication.run(EkycApplication.class, args);
    }

    @Bean
    XmlSigner xmlSigner() {
        return new XmlSigner(KeyMaterial.generate("eKYC"));
    }

    @Bean
    XmlHttp xmlHttp() {
        return new XmlHttp();
    }

    @Bean
    org.springframework.boot.ApplicationRunner seed(AccountDirectory accounts, EkycProperties properties, AuditLog audit) {
        return args -> {
            if (properties.isSeedDemoUser()) {
                accounts.seedDemo();
                audit.add("SEED", "", "Demo signer demo.user@username.ESP001 is ready");
            }
        };
    }
}
