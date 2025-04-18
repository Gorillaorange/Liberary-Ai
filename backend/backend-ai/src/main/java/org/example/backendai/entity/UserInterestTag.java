package org.example.backendai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户兴趣标签实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInterestTag {
    /**
     * 记录ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 兴趣标签名称
     */
    private String tagName;
    
    /**
     * 权重值 (1-100)
     */
    private Integer weight;
    
    /**
     * 出现次数
     */
    private Integer occurrenceCount;
    
    /**
     * 首次出现时间
     */
    private LocalDateTime firstOccurrenceTime;
    
    /**
     * 最近出现时间
     */
    private LocalDateTime lastOccurrenceTime;
} 