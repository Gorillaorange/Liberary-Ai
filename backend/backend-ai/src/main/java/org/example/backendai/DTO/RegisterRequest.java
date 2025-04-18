package org.example.backendai.DTO;

import lombok.Data;

/**
 * 注册请求DTO
 */
@Data
public class RegisterRequest {
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 确认密码
     */
    private String confirmPassword;
    
    /**
     * 年级（如：2021）
     */
    private String grade;
    
    /**
     * 专业
     */
    private String major;
} 