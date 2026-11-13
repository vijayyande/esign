package com.esign.esp;

import com.esign.common.crypto.KeyMaterial;
import com.esign.common.crypto.XmlSigner;
import com.esign.common.http.XmlHttp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.esign.esp", "com.esign.common"})
@EnableConfigurationProperties(EspProperties.class)
@EnableScheduling
public class EspApplication {
    public static void main(String[] args) {
        SpringApplication.run(EspApplication.class, args);
    }

    @Bean
    XmlSigner xmlSigner() {
        return new XmlSigner(KeyMaterial.generate("ESP001"));
    }

    @Bean
    XmlHttp xmlHttp() {
        return new XmlHttp();
    }
}
