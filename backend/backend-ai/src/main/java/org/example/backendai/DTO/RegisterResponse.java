package org.example.backendai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    /**
     * 错误码，0表示成功
     */
    private Integer error;
    
    /**
     * 错误信息
     */
    private String msg;
    
    /**
     * 返回数据
     */
    private RegisterData data;
    
    /**
     * 注册成功数据，包含用户信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterData {
        /**
         * 用户名
         */
        private String username;
        
        /**
         * 年级
         */
        private String grade;
        
        /**
         * 专业
         */
        private String major;
    }
} 