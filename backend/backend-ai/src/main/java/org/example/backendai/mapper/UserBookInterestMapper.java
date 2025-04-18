package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.entity.UserBookInterest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户书籍兴趣数据访问接口
 */
@Mapper
@Repository
public interface UserBookInterestMapper {
    
    /**
     * 根据用户ID和书籍ID查找兴趣记录
     *
     * @param userId 用户ID
     * @param bookId 书籍ID
     * @return 用户书籍兴趣记录
     */
    @Select("SELECT * FROM user_book_interest WHERE user_id = #{userId} AND book_id = #{bookId} LIMIT 1")
    UserBookInterest findByUserIdAndBookId(@Param("userId") Long userId, @Param("bookId") String bookId);
    
    /**
     * 查询用户所有的书籍兴趣记录
     *
     * @param userId 用户ID
     * @return 兴趣记录列表
     */
    @Select("SELECT * FROM user_book_interest WHERE user_id = #{userId} " +
            "ORDER BY interest_level DESC, last_mention_time DESC")
    List<UserBookInterest> findByUserId(@Param("userId") Long userId);
    
    /**
     * 查询用户最感兴趣的书籍记录
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 兴趣记录列表
     */
    @Select("SELECT * FROM user_book_interest WHERE user_id = #{userId} " +
            "ORDER BY interest_level DESC, last_mention_time DESC LIMIT #{limit}")
    List<UserBookInterest> findTopInterestsByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    
    /**
     * 插入兴趣记录
     *
     * @param interest 兴趣记录
     * @return 影响行数
     */
    @Insert("INSERT INTO user_book_interest (id, user_id, book_id, interest_level, mention_count, " +
            "first_mention_time, last_mention_time) " +
            "VALUES (#{id}, #{userId}, #{bookId}, #{interestLevel}, #{mentionCount}, " +
            "#{firstMentionTime}, #{lastMentionTime})")
    int insert(UserBookInterest interest);
    
    /**
     * 更新兴趣记录
     *
     * @param interest 兴趣记录
     * @return 影响行数
     */
    @Update("UPDATE user_book_interest SET interest_level = #{interestLevel}, " +
            "mention_count = #{mentionCount}, last_mention_time = #{lastMentionTime} " +
            "WHERE id = #{id}")
    int update(UserBookInterest interest);
    
    /**
     * 删除兴趣记录
     *
     * @param id 记录ID
     * @return 影响行数
     */
    @Delete("DELETE FROM user_book_interest WHERE id = #{id}")
    int deleteById(@Param("id") String id);
    
    /**
     * 删除用户所有兴趣记录
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    @Delete("DELETE FROM user_book_interest WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
} 