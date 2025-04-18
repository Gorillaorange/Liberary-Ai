package org.example.backendai.service;

import org.example.backendai.DTO.ChatSessionDTO;
import org.example.backendai.entity.ChatSession;

import java.util.List;

/**
 * 聊天会话服务接口
 */
public interface ChatSessionService {
    
    /**
     * 创建新的聊天会话
     */
    ChatSessionDTO createSession(Long userId, String title);
    
    /**
     * 更新聊天会话
     */
    boolean updateSession(String id, String title, Long userId);
    
    /**
     * 获取聊天会话详情
     */
    ChatSessionDTO getSessionById(String id, Long userId);
    
    /**
     * 获取用户的所有聊天会话
     */
    List<ChatSessionDTO> getUserSessions(Long userId);
    
    /**
     * 删除聊天会话
     */
    boolean deleteSession(String id, Long userId);
    
    /**
     * 清空用户所有会话
     */
    boolean clearUserSessions(Long userId);
    
    /**
     * 更新会话最后一条消息预览
     */
    boolean updateLastMessage(String sessionId, String lastMessagePreview);
} 