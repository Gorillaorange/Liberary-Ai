package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.entity.UserInterestTag;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户兴趣标签数据访问接口
 */
@Mapper
@Repository
public interface UserInterestTagMapper {
    
    /**
     * 根据用户ID和标签名查找记录
     *
     * @param userId 用户ID
     * @param tagName 标签名
     * @return 用户兴趣标签记录
     */
    @Select("SELECT * FROM user_interest_tag WHERE user_id = #{userId} AND tag_name = #{tagName} LIMIT 1")
    UserInterestTag findByUserIdAndTagName(@Param("userId") Long userId, @Param("tagName") String tagName);
    
    /**
     * 查询用户所有的兴趣标签
     *
     * @param userId 用户ID
     * @return 标签记录列表
     */
    @Select("SELECT * FROM user_interest_tag WHERE user_id = #{userId} " +
            "ORDER BY weight DESC, last_occurrence_time DESC")
    List<UserInterestTag> findByUserId(@Param("userId") Long userId);
    
    /**
     * 查询用户的热门标签
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 标签记录列表
     */
    @Select("SELECT tag_name FROM user_interest_tag WHERE user_id = #{userId} " +
            "ORDER BY weight DESC, last_occurrence_time DESC LIMIT #{limit}")
    List<String> findTopTagsByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    
    /**
     * 插入标签记录
     *
     * @param tag 标签记录
     * @return 影响行数
     */
    @Insert("INSERT INTO user_interest_tag (id, user_id, tag_name, weight, occurrence_count, " +
            "first_occurrence_time, last_occurrence_time) " +
            "VALUES (#{id}, #{userId}, #{tagName}, #{weight}, #{occurrenceCount}, " +
            "#{firstOccurrenceTime}, #{lastOccurrenceTime})")
    int insert(UserInterestTag tag);
    
    /**
     * 更新标签记录
     *
     * @param tag 标签记录
     * @return 影响行数
     */
    @Update("UPDATE user_interest_tag SET weight = #{weight}, " +
            "occurrence_count = #{occurrenceCount}, last_occurrence_time = #{lastOccurrenceTime} " +
            "WHERE id = #{id}")
    int update(UserInterestTag tag);
    
    /**
     * 删除标签记录
     *
     * @param id 记录ID
     * @return 影响行数
     */
    @Delete("DELETE FROM user_interest_tag WHERE id = #{id}")
    int deleteById(@Param("id") String id);
    
    /**
     * 删除用户所有标签记录
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Delete("DELETE FROM user_interest_tag WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
} 