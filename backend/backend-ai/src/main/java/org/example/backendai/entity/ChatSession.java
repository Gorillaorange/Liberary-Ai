package org.example.backendai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 聊天会话实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    /**
     * 会话ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 会话标题
     */
    private String title;
    
    /**
     * 创建时间
     */
    private Date createdAt;
    
    /**
     * 更新时间
     */
    private Date updatedAt;
    
    /**
     * 最后一条消息内容预览
     */
    private String lastMessagePreview;
    
    /**
     * 状态（0-已删除，1-正常）
     */
    private Integer status;
} 