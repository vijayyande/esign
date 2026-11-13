package com.esign.asp;

import com.esign.common.crypto.XmlSigner;
import com.esign.common.http.XmlHttp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.esign.asp", "com.esign.common"})
@EnableConfigurationProperties(AspProperties.class)
public class AspApplication {
    public static void main(String[] args) {
        SpringApplication.run(AspApplication.class, args);
    }

    @Bean
    XmlSigner xmlSigner(AspProperties properties) {
        return new XmlSigner(AspSigningKeys.load(properties));
    }

    @Bean
    XmlHttp xmlHttp() {
        return new XmlHttp();
    }
}
