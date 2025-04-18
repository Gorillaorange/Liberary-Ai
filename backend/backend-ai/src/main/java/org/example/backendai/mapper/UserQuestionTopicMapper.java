package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.entity.UserQuestionTopic;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户问题主题数据访问接口
 */
@Mapper
@Repository
public interface UserQuestionTopicMapper {
    
    /**
     * 根据用户ID和主题查找问题记录
     *
     * @param userId 用户ID
     * @param topic 主题
     * @return 问题主题记录
     */
    @Select("SELECT * FROM user_question_topic WHERE user_id = #{userId} AND topic = #{topic} LIMIT 1")
    UserQuestionTopic findByUserIdAndTopic(@Param("userId") Long userId, @Param("topic") String topic);
    
    /**
     * 根据用户ID查询问题主题，按提问次数排序
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 问题主题列表
     */
    @Select("SELECT * FROM user_question_topic WHERE user_id = #{userId} " +
            "ORDER BY count DESC LIMIT #{limit}")
    List<UserQuestionTopic> findByUserIdOrderByCount(@Param("userId") Long userId, @Param("limit") int limit);
    
    /**
     * 插入问题主题记录
     *
     * @param topic 问题主题记录
     * @return 影响行数
     */
    @Insert("INSERT INTO user_question_topic (id, user_id, topic, count, last_question, created_at, updated_at) " +
            "VALUES (#{id}, #{userId}, #{topic}, #{count}, #{lastQuestion}, #{createdAt}, #{updatedAt})")
    int insert(UserQuestionTopic topic);
    
    /**
     * 更新问题主题记录
     *
     * @param topic 问题主题记录
     * @return 影响行数
     */
    @Update("UPDATE user_question_topic SET count = #{count}, last_question = #{lastQuestion}, " +
            "updated_at = #{updatedAt} WHERE id = #{id}")
    int update(UserQuestionTopic topic);
    
    /**
     * 删除用户的所有问题主题记录
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Delete("DELETE FROM user_question_topic WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
} 