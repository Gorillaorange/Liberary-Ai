package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.entity.ChatSession;

import java.util.List;

/**
 * 聊天会话Mapper接口
 */
@Mapper
public interface ChatSessionMapper {
    
    /**
     * 创建新的聊天会话
     */
    @Insert("INSERT INTO chat_session (id, user_id, title, last_message_preview) " +
            "VALUES (#{id}, #{userId}, #{title}, #{lastMessagePreview})")
    @Options(useGeneratedKeys = false)
    int insert(ChatSession chatSession);
    
    /**
     * 更新聊天会话
     */
    @Update("UPDATE chat_session SET title = #{title}, last_message_preview = #{lastMessagePreview} WHERE id = #{id}")
    int update(ChatSession chatSession);
    
    /**
     * 根据ID查询聊天会话
     */
    @Select("SELECT * FROM chat_session WHERE id = #{id}")
    ChatSession selectById(String id);
    
    /**
     * 根据用户ID查询聊天会话列表
     */
    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<ChatSession> selectByUserId(Long userId);
    
    /**
     * 删除聊天会话
     */
    @Delete("DELETE FROM chat_session WHERE id = #{id} AND user_id = #{userId}")
    int delete(@Param("id") String id, @Param("userId") Long userId);
    
    /**
     * 清空用户所有会话
     */
    @Delete("DELETE FROM chat_session WHERE user_id = #{userId}")
    int deleteAllByUserId(Long userId);
} 