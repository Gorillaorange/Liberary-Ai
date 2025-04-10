package org.example.backendai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class WebClientConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .filter((request, next) -> {
                    logger.info("发送请求: {} {}", request.method(), request.url());
                    return next.exchange(request)
                            .timeout(Duration.ofSeconds(300))
                            .doOnError(error -> 
                                logger.error("请求失败: {} - {}", error.getClass().getName(), error.getMessage())
                            );
                })
                .filter((request, next) -> {
                    return next.exchange(request)
                            .retry(2)
                            .onErrorResume(error -> {
                                logger.error("重试后仍然失败: {}", error.getMessage());
                                return Mono.error(error);
                            });
                });
    }
    
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
} 