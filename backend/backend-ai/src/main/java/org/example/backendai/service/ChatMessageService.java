package org.example.backendai.service;

import org.example.backendai.DTO.ChatMessageDTO;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息服务接口
 */
public interface ChatMessageService {
    
    /**
     * 添加聊天消息
     */
    ChatMessageDTO addMessage(String sessionId, Long userId, String role, String content);
    
    /**
     * 获取会话的消息列表
     */
    List<ChatMessageDTO> getSessionMessages(String sessionId, Long userId);

    /**
     * 获取用户的所有聊天消息
     * 
     * @param userId 用户ID
     * @return 消息列表，每条消息包含角色、内容等信息
     */
    List<Map<String, Object>> getAllUserMessages(Long userId);
} 