package org.example.backendai.config;

import org.springframework.context.annotation.Configuration;
import java.util.Arrays;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 明确允许的前端地址，可以添加多个
                .allowedOrigins(
                    "http://localhost:3000",   // 前端开发服务器
                    "http://localhost:8080",   // 后端开发服务器(同源)
                    "http://127.0.0.1:3000",   // 本地IP访问
                    "http://127.0.0.1:8080"    // 本地IP访问
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With") // 允许的请求头
                .exposedHeaders("Authorization", "Content-Type", "Clear-Auth") // 暴露的响应头
                .allowCredentials(true) // 允许发送凭证
                .maxAge(3600L); // 预检请求的有效期，单位为秒
    }

}
