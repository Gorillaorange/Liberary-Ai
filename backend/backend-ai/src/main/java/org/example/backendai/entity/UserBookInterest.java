package org.example.backendai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户书籍兴趣实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBookInterest {
    /**
     * 记录ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 书籍ID
     */
    private String bookId;
    
    /**
     * 兴趣级别 (1-5)
     */
    private Integer interestLevel;
    
    /**
     * 提及次数
     */
    private Integer mentionCount;
    
    /**
     * 首次提及时间
     */
    private LocalDateTime firstMentionTime;
    
    /**
     * 最近提及时间
     */
    private LocalDateTime lastMentionTime;
    
    /**
     * 关联的书籍(非数据库字段)
     */
    private Book book;
} 