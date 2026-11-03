package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SoftixConfig {

                @Bean
                RestClient softixRestClient(@Value("${softix.base-url}") String baseUrl,
                                                @Value("${softix.connect-timeout-ms}") long connectTimeoutMs,
                                                @Value("${softix.read-timeout-ms}") long readTimeoutMs) {
                                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                                requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
                                requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
                                return RestClient.builder().baseUrl(baseUrl)
                                                                .requestFactory(requestFactory).build();
                }
}
