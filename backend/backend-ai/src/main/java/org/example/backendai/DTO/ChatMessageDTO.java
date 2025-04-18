package org.example.backendai.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 聊天消息数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    /**
     * 消息ID
     */
    private String id;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 消息角色（user-用户，assistant-AI助手）
     */
    private String role;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 创建时间
     */
    private Date createTime;
} 