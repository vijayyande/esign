package com.esign.ca;

import com.esign.common.crypto.DocumentSignatures;
import com.esign.common.crypto.KeyMaterial;
import com.esign.common.crypto.XmlSigner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.esign.ca", "com.esign.common"})
@EnableConfigurationProperties(CaProperties.class)
public class CaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaApplication.class, args);
    }

    @Bean
    XmlSigner xmlSigner() {
        return new XmlSigner(KeyMaterial.generate("CA"));
    }
}
