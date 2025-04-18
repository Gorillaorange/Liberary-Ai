package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.entity.ChatMessage;

import java.util.List;

/**
 * 聊天消息Mapper接口
 */
@Mapper
public interface ChatMessageMapper {
    
    /**
     * 添加聊天消息
     */
    @Insert("INSERT INTO chat_message (id, session_id, user_id, role, content, create_time) " +
            "VALUES (#{id}, #{sessionId}, #{userId}, #{role}, #{content}, #{createTime})")
    @Options(useGeneratedKeys = false)
    int insert(ChatMessage chatMessage);
    
    /**
     * 查询会话的消息列表
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatMessage> selectBySessionId(String sessionId);
    
    /**
     * 查询会话最近的一条消息
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY create_time DESC LIMIT 1")
    ChatMessage selectLastMessageBySessionId(String sessionId);
    
    /**
     * 删除会话的所有消息（逻辑删除）
     */
    @Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);
} 