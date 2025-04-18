package org.example.backendai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 书籍实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {
    /**
     * 书籍ID
     */
    private String id;
    
    /**
     * 书籍标题
     */
    private String title;
    
    /**
     * 作者
     */
    private String author;
    
    /**
     * 类别
     */
    private String category;
    
    /**
     * 简介
     */
    private String description;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
} 