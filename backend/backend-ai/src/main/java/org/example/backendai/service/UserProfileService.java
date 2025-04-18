package org.example.backendai.service;

import org.example.backendai.entity.Book;
import org.example.backendai.entity.UserBookInterest;
import org.example.backendai.entity.UserQuestionTopic;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户个性化信息服务接口
 */
public interface UserProfileService {
    
    /**
     * 处理用户问题，提取并保存个性化信息
     *
     * @param userId 用户ID
     * @param question 用户问题
     * @param topic 问题主题(可选)
     */
    @Transactional
    void processUserQuestion(Long userId, String question, String topic);
    
    /**
     * 更新用户问题主题
     * 
     * @param userId 用户ID
     * @param topic 问题主题
     * @param question 问题内容
     */
    @Transactional
    void updateQuestionTopic(Long userId, String topic, String question);
    
    /**
     * 提取并处理书籍信息
     * 
     * @param userId 用户ID
     * @param text 待提取文本
     */
    @Transactional
    void extractAndProcessBooks(Long userId, String text);
    
    /**
     * 处理书籍提及
     * 
     * @param userId 用户ID
     * @param bookTitle 书籍标题
     */
    @Transactional
    void processBookMention(Long userId, String bookTitle);
    
    /**
     * 获取用户感兴趣的书籍列表
     *
     * @param userId 用户ID
     * @return 书籍列表
     */
    List<Book> getUserInterestedBooks(Long userId);
    
    /**
     * 获取用户的主要问题主题
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 主题列表
     */
    List<UserQuestionTopic> getUserTopQuestions(Long userId, int limit);
    
    /**
     * 获取用户对某本书的兴趣详情
     *
     * @param userId 用户ID
     * @param bookId 书籍ID
     * @return 兴趣详情，不存在则返回null
     */
    UserBookInterest getUserBookInterest(Long userId, String bookId);
    
    /**
     * 根据关键词搜索书籍
     *
     * @param keyword 关键词
     * @return 书籍列表
     */
    List<Book> searchBooks(String keyword);
    
    /**
     * 清空用户的所有个性化记录
     *
     * @param userId 用户ID
     */
    @Transactional
    void clearUserProfile(Long userId);
    
    /**
     * 保存用户兴趣标签
     *
     * @param userId 用户ID
     * @param interests 兴趣标签列表
     */
    @Transactional
    void saveUserInterests(Long userId, List<String> interests);
} 