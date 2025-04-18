package org.example.backendai.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/**
 * 密码工具类
 */
@Slf4j
@Component
public class PasswordUtil {
    
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10); // 使用强度为10的BCrypt
    
    /**
     * 生成加密密码
     * @param rawPassword 原始密码（前端MD5加密后的密码）
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        // 再进行BCrypt加密
        String encodedPassword = encoder.encode(rawPassword);
        log.info("密码加密 - MD5密码: {}, BCrypt密码: {}", rawPassword, encodedPassword);
        return encodedPassword;
    }
    
    /**
     * 验证密码
     * @param rawPassword 原始密码（前端MD5加密后的密码）
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        try {
            boolean matches = encoder.matches(rawPassword, encodedPassword);
            log.info("密码验证 - MD5密码: {}, 加密密码: {}, 验证结果: {}", 
                rawPassword, encodedPassword, matches);
            return matches;
        } catch (Exception e) {
            log.error("密码验证失败", e);
            return false;
        }
    }
    
    public static void main(String[] args) {
        // 生成测试密码
        String password = "123456";
        String md5Password = md5(password);
        String encodedPassword = encode(md5Password);
        System.out.println("原始密码: " + password);
        System.out.println("MD5密码: " + md5Password);
        System.out.println("BCrypt密码: " + encodedPassword);
        System.out.println("验证结果: " + matches(md5Password, encodedPassword));
        
        // 测试数据库中的密码
        String dbPassword = "$2a$10$X/TQ/m.Z.rZ.YiEQbAz1S.1nMY.bHJRMVHBxcJVvp.m3kPLqnQLsC";
        System.out.println("验证数据库密码结果: " + matches(md5Password, dbPassword));
    }
    
    /**
     * 计算字符串的MD5哈希值
     * @param input 输入字符串
     * @return MD5哈希值
     */
    private static String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5加密失败", e);
            throw new RuntimeException("MD5加密失败", e);
        }
    }
} 