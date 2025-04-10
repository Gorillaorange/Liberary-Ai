package org.example.backendai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户问题主题实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestionTopic {
    /**
     * 记录ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 问题主题
     */
    private String topic;
    
    /**
     * 提问次数
     */
    private Integer count;
    
    /**
     * 最近一次提问内容
     */
    private String lastQuestion;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
} 