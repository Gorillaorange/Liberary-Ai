package org.example.backendai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户信息请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserInfoRequest {
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 年级（如：2021）
     */
    private String grade;
    
    /**
     * 专业
     */
    private String major;
} 