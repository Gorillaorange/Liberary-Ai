package org.example.backendai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新密码请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePasswordRequest {
    /**
     * 旧密码
     */
    private String oldPassword;
    
    /**
     * 新密码
     */
    private String newPassword;
} 