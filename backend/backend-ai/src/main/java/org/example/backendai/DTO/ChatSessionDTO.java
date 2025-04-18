package org.example.backendai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 聊天会话数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDTO {
    /**
     * 会话ID
     */
    private String id;
    
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
} 