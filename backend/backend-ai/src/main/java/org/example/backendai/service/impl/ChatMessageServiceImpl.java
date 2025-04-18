package org.example.backendai.service.impl;

import org.example.backendai.DTO.ChatMessageDTO;
import org.example.backendai.entity.ChatMessage;
import org.example.backendai.entity.ChatSession;
import org.example.backendai.mapper.ChatMessageMapper;
import org.example.backendai.mapper.ChatSessionMapper;
import org.example.backendai.service.ChatMessageService;
import org.example.backendai.service.ChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天消息服务实现类
 */
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    
    @Autowired
    private ChatSessionService chatSessionService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public ChatMessageDTO addMessage(String sessionId, Long userId, String role, String content) {
        // 检查会话是否存在并验证用户权限
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !validateUserAccess(session, userId)) {
            return null;
        }
        
        // 创建消息，生成UUID作为ID
        String messageId = UUID.randomUUID().toString();
        ChatMessage chatMessage = ChatMessage.builder()
                .id(messageId)
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .content(content)
                .createTime(new Date())
                .build();
        
        try {
            int result = chatMessageMapper.insert(chatMessage);
            if (result <= 0) {
                return null;
            }
            
            // 更新会话的最后一条消息预览
            String preview = content;
            if (preview.length() > 50) {
                preview = preview.substring(0, 47) + "...";
            }
            chatSessionService.updateLastMessage(sessionId, preview);
            
            return convertToDTO(chatMessage);
        } catch (Exception e) {
            logger.error("添加消息失败: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<ChatMessageDTO> getSessionMessages(String sessionId, Long userId) {
        // 检查会话是否存在并验证用户权限
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !validateUserAccess(session, userId)) {
            return null;
        }
        
        List<ChatMessage> messages = chatMessageMapper.selectBySessionId(sessionId);
        
        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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
            logger.debug("用户ID不匹配，会话userId: {}, 请求userId: {}", session.getUserId(), userId);
            return false;
        }
        return true;
    }
    
    /**
     * 实体转DTO
     */
    private ChatMessageDTO convertToDTO(ChatMessage chatMessage) {
        ChatMessageDTO dto = new ChatMessageDTO();
        BeanUtils.copyProperties(chatMessage, dto);
        return dto;
    }

    /**
     * 获取用户的所有聊天消息
     *
     * @param userId 用户ID
     * @return 消息列表，每条消息包含角色、内容等信息
     */
    @Override
    public List<Map<String, Object>> getAllUserMessages(Long userId) {
        try {
            String sql = "SELECT id, session_id, user_id, role, content, create_time FROM chat_message " +
                        "WHERE user_id = ? ORDER BY create_time ASC";
            
            return jdbcTemplate.queryForList(sql, userId);
        } catch (Exception e) {
            logger.error("获取用户所有消息失败", e);
            return Collections.emptyList();
        }
    }
} 