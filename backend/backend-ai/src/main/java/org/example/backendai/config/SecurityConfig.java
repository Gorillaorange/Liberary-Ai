package org.example.backendai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Spring Security配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    public SecurityConfig(@Lazy JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 配置安全过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护
            .csrf(AbstractHttpConfigurer::disable)
            // 允许跨域
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 配置会话管理，使用无状态会话
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求授权
            .authorizeHttpRequests(auth -> auth
                // 登录接口允许匿名访问
                .requestMatchers("/api/auth/login").permitAll()
                // 注册接口允许匿名访问
                .requestMatchers("/api/auth/register").permitAll()
                // 退出登录接口也允许匿名访问
                .requestMatchers("/api/auth/logout").permitAll()
                // 静态资源允许匿名访问
                .requestMatchers("/", "/index.html", "/static/**", "/public/**", "/favicon.ico").permitAll()
                // 放行聊天接口
                .requestMatchers("/chat").permitAll()
                // 放行会话消息接口
                .requestMatchers("/api/sessions/**").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            // 添加JWT认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    /**
     * 提供密码编码器
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * 配置CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 设置允许的域名
        configuration.setAllowedOriginPatterns(List.of("*"));
        
        // 设置允许的方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // 设置允许的头部
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With", 
            "Accept", 
            "Origin", 
            "Access-Control-Request-Method", 
            "Access-Control-Request-Headers"
        ));
        
        // 设置暴露的头部
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "Access-Control-Allow-Origin", 
            "Access-Control-Allow-Credentials"
        ));
        
        // 允许凭证
        configuration.setAllowCredentials(true);
        
        // 预检请求的有效期，单位秒
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}