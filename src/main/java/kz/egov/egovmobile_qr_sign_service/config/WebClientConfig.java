package kz.egov.egovmobile_qr_sign_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ncanode.url}")
    private String ncanodeUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder().baseUrl(ncanodeUrl).build();
    }
}
