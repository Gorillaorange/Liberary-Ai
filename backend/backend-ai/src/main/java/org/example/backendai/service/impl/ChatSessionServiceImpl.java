package org.example.backendai.service.impl;

import org.example.backendai.DTO.ChatSessionDTO;
import org.example.backendai.entity.ChatSession;
import org.example.backendai.mapper.ChatMessageMapper;
import org.example.backendai.mapper.ChatSessionMapper;
import org.example.backendai.service.ChatSessionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天会话服务实现类
 */
@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionServiceImpl.class);

    @Autowired
    private ChatSessionMapper chatSessionMapper;
    
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Override
    public ChatSessionDTO createSession(Long userId, String title) {
        if (userId == null) {
            logger.warn("创建会话失败：userId为null");
            return null;
        }
        
        // 生成唯一ID
        String sessionId = UUID.randomUUID().toString();
        
        ChatSession chatSession = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .title(title)
                .lastMessagePreview("")
                .build();
        
        try {
            int result = chatSessionMapper.insert(chatSession);
            if (result > 0) {
                return convertToDTO(chatSession);
            } else {
                logger.error("创建会话失败：数据库插入返回0");
                return null;
            }
        } catch (Exception e) {
            logger.error("创建会话异常", e);
            throw e; // 重新抛出异常，让上层知道具体错误
        }
    }
    
    @Override
    public boolean updateSession(String id, String title, Long userId) {
        logger.info("开始更新会话: id={}, title={}, userId={}", id, title, userId);
        
        ChatSession chatSession = chatSessionMapper.selectById(id);
        if (chatSession == null) {
            logger.warn("更新会话失败: 会话不存在 id={}", id);
            return false;
        }
        
        // 验证用户权限
        if (!validateUserAccess(chatSession, userId)) {
            logger.warn("更新会话失败: 用户无权限 id={}, userId={}, sessionUserId={}", 
                      id, userId, chatSession.getUserId());
            return false;
        }
        
        chatSession.setTitle(title);
        chatSession.setUpdatedAt(new Date());
        
        int result = chatSessionMapper.update(chatSession);
        if (result > 0) {
            logger.info("会话更新成功: id={}, title={}", id, title);
            return true;
        } else {
            logger.warn("会话更新失败: 数据库更新返回0, id={}", id);
            return false;
        }
    }
    
    @Override
    public ChatSessionDTO getSessionById(String id, Long userId) {
        ChatSession chatSession = chatSessionMapper.selectById(id);
        if (chatSession == null) {
            return null;
        }
        
        // 验证用户权限
        if (!validateUserAccess(chatSession, userId)) {
            return null;
        }
        
        return convertToDTO(chatSession);
    }
    
    @Override
    public List<ChatSessionDTO> getUserSessions(Long userId) {
        List<ChatSession> chatSessions = chatSessionMapper.selectByUserId(userId);
        
        return chatSessions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public boolean deleteSession(String id, Long userId) {
        logger.info("开始删除会话: id={}, userId={}", id, userId);
        
        // 删除会话
        int result = chatSessionMapper.delete(id, userId);
        if (result > 0) {
            // 删除会话下的所有消息
            int messagesDeleted = chatMessageMapper.deleteBySessionId(id);
            logger.info("会话删除成功: id={}, 同时删除相关消息数量: {}", id, messagesDeleted);
            return true;
        } else {
            logger.warn("会话删除失败: 会话不存在或用户无权限, id={}, userId={}", id, userId);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean clearUserSessions(Long userId) {
        logger.info("开始清空用户所有会话: userId={}", userId);
        
        // 获取用户的所有会话
        List<ChatSession> sessions = chatSessionMapper.selectByUserId(userId);
        logger.info("用户会话总数: userId={}, count={}", userId, sessions.size());
        
        // 删除用户的所有会话
        int result = chatSessionMapper.deleteAllByUserId(userId);
        if (result > 0) {
            // 删除所有会话下的消息
            int totalMessagesDeleted = 0;
            for (ChatSession session : sessions) {
                int messagesDeleted = chatMessageMapper.deleteBySessionId(session.getId());
                totalMessagesDeleted += messagesDeleted;
            }
            logger.info("清空用户所有会话成功: userId={}, 删除会话数量: {}, 删除消息数量: {}", 
                      userId, result, totalMessagesDeleted);
            return true;
        } else {
            logger.warn("清空用户所有会话失败: 用户不存在或没有会话, userId={}", userId);
            return false;
        }
    }
    
    @Override
    public boolean updateLastMessage(String sessionId, String lastMessagePreview) {
        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if (chatSession == null) {
            return false;
        }
        
        chatSession.setLastMessagePreview(lastMessagePreview);
        
        return chatSessionMapper.update(chatSession) > 0;
    }
    
    /**
     * 验证用户对会话的访问权限，必要时更新会话用户ID
     *
     * @param session 会话对象
     * @param userId 用户ID
     * @return 是否允许访问
     */
    private boolean validateUserAccess(ChatSession session, Long userId) {
        if (session.getUserId() == null) {
            // 如果会话没有关联用户ID，则将当前用户ID关联到会话
            if (userId != null) {
                session.setUserId(userId);
                chatSessionMapper.update(session);
            }
            return true;
        } else if (userId != null && !session.getUserId().equals(userId)) {
            // 用户ID不匹配，拒绝访问
            return false;
        }
        return true;
    }
    
    /**
     * 实体转DTO
     */
    private ChatSessionDTO convertToDTO(ChatSession chatSession) {
        ChatSessionDTO dto = new ChatSessionDTO();
        BeanUtils.copyProperties(chatSession, dto);
        return dto;
    }
} 