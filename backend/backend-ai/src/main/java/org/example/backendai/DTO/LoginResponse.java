package org.example.backendai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
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
    private LoginData data;
    
    /**
     * 登录数据，包含令牌等信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginData {
        /**
         * 访问令牌
         */
        private String token;
        
        /**
         * 用户名
         */
        private String username;
    }
} 