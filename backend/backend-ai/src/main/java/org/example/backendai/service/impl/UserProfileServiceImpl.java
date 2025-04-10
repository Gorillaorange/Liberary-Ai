package org.example.backendai.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.backendai.entity.Book;
import org.example.backendai.entity.UserBookInterest;
import org.example.backendai.entity.UserQuestionTopic;
import org.example.backendai.mapper.BookMapper;
import org.example.backendai.mapper.UserBookInterestMapper;
import org.example.backendai.mapper.UserQuestionTopicMapper;
import org.example.backendai.service.UserProfileService;
import org.example.backendai.service.UserInterestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户个性化信息服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    
    private final BookMapper bookMapper;
    private final UserBookInterestMapper userBookInterestMapper;
    private final UserQuestionTopicMapper userQuestionTopicMapper;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    private UserInterestService userInterestService;
    
    private static final Logger logger = LoggerFactory.getLogger(UserProfileServiceImpl.class);
    
    // 书名匹配正则表达式 - 匹配《书名》格式或者"书名"这本书 格式
    private static final Pattern BOOK_PATTERN = Pattern.compile("《([^》]+)》|\"([^\"]+)\"这本书|\"([^\"]+)\"这书");
    
    @Override
    @Transactional
    public void processUserQuestion(Long userId, String question, String topic) {
        if (userId == null || question == null || question.isEmpty()) {
            return;
        }
        
        // 处理问题主题
        if (topic != null && !topic.isEmpty()) {
            updateQuestionTopic(userId, topic, question);
        }
        
        // 从问题中提取书籍信息
        extractAndProcessBooks(userId, question);
    }
    
    @Override
    @Transactional
    public void updateQuestionTopic(Long userId, String topic, String question) {
        if (topic == null || topic.isEmpty()) {
            return;
        }
        
        // 查找用户是否已有该主题的问题记录
        UserQuestionTopic existingTopic = userQuestionTopicMapper.findByUserIdAndTopic(userId, topic);
        
        if (existingTopic == null) {
            // 创建新主题记录
            UserQuestionTopic newTopic = new UserQuestionTopic();
            newTopic.setUserId(userId);
            newTopic.setTopic(topic);
            newTopic.setCount(1);
            newTopic.setLastQuestion(question);
            newTopic.setCreatedAt(LocalDateTime.now());
            newTopic.setUpdatedAt(LocalDateTime.now());
            
            userQuestionTopicMapper.insert(newTopic);
        } else {
            // 更新现有主题记录
            existingTopic.setCount(existingTopic.getCount() + 1);
            existingTopic.setLastQuestion(question);
            existingTopic.setUpdatedAt(LocalDateTime.now());
            
            userQuestionTopicMapper.update(existingTopic);
        }
    }
    
    @Override
    @Transactional
    public void extractAndProcessBooks(Long userId, String text) {
        Matcher matcher = BOOK_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String bookTitle;
            if (matcher.group(1) != null) {
                bookTitle = matcher.group(1); // 《书名》格式
            } else if (matcher.group(2) != null) {
                bookTitle = matcher.group(2); // "书名"这本书 格式
            } else {
                bookTitle = matcher.group(3); // "书名"这书 格式
            }
            
            if (bookTitle != null && !bookTitle.isEmpty()) {
                processBookMention(userId, bookTitle);
            }
        }
    }
    
    @Override
    @Transactional
    public void processBookMention(Long userId, String bookTitle) {
        // 查找书籍是否存在
        Book book = bookMapper.findByTitle(bookTitle);
        
        if (book == null) {
            // 创建新书籍记录（基础信息）
            book = Book.builder()
                    .title(bookTitle)
                    .author("未知") // 默认作者
                    .category("未分类") // 默认分类
                    .description("用户提及的书籍") // 默认描述
                    .createdAt(LocalDateTime.now())
                    .build();
            
            bookMapper.insert(book);
        }
        
        // 查找用户对该书籍的兴趣记录
        UserBookInterest interest = userBookInterestMapper.findByUserIdAndBookId(userId, book.getId());
        
        if (interest == null) {
            // 创建新的兴趣记录
            interest = UserBookInterest.builder()
                    .userId(userId)
                    .bookId(book.getId())
                    .interestLevel(1) // 初始兴趣度
                    .mentionCount(1) // 首次提及
                    .firstMentionTime(LocalDateTime.now())
                    .lastMentionTime(LocalDateTime.now())
                    .build();
            
            userBookInterestMapper.insert(interest);
        } else {
            // 更新兴趣记录
            interest.setMentionCount(interest.getMentionCount() + 1);
            interest.setInterestLevel(Math.min(5, interest.getMentionCount())); // 最高兴趣度为5
            interest.setLastMentionTime(LocalDateTime.now());
            
            userBookInterestMapper.update(interest);
        }
    }
    
    @Override
    public List<Book> getUserInterestedBooks(Long userId) {
        try {
            return bookMapper.findUserInterestedBooks(userId);
        } catch (Exception e) {
            logger.error("获取用户感兴趣的书籍失败: ", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<Book> searchBooks(String keyword) {
        try {
            return bookMapper.searchByTitleKeyword("%" + keyword + "%");
        } catch (Exception e) {
            logger.error("搜索书籍失败: ", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<UserQuestionTopic> getUserTopQuestions(Long userId, int limit) {
        try {
            return userQuestionTopicMapper.findByUserIdOrderByCount(userId, limit);
        } catch (Exception e) {
            logger.error("获取用户问题主题失败: ", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public UserBookInterest getUserBookInterest(Long userId, String bookId) {
        try {
            return userBookInterestMapper.findByUserIdAndBookId(userId, bookId);
        } catch (Exception e) {
            logger.error("获取用户书籍兴趣失败: ", e);
            return null;
        }
    }
    
    @Override
    @Transactional
    public void clearUserProfile(Long userId) {
        try {
            // 清除用户书籍兴趣记录
            userBookInterestMapper.deleteByUserId(userId);
            
            // 清除用户问题主题记录
            userQuestionTopicMapper.deleteByUserId(userId);
            
            // 清除用户兴趣标签记录
            userInterestService.deleteUserInterests(userId);
            
            logger.info("已清除用户[{}]的个性化数据", userId);
        } catch (Exception e) {
            logger.error("清除用户个性化数据失败: ", e);
            throw e;
        }
    }
    
    /**
     * 保存用户兴趣标签
     *
     * @param userId 用户ID
     * @param interests 兴趣标签列表
     */
    @Override
    @Transactional
    public void saveUserInterests(Long userId, List<String> interests) {
        try {
            logger.info("开始保存用户 {} 的兴趣标签: {}", userId, interests);
            
            // 使用UserInterestService保存到user_interest_tag表
            if (interests != null && !interests.isEmpty()) {
                int savedCount = userInterestService.saveUserInterests(userId, interests);
                logger.info("用户 {} 的兴趣标签保存成功，共保存 {} 个标签", userId, savedCount);
            } else {
                logger.warn("用户 {} 的兴趣标签列表为空，未保存", userId);
            }
        } catch (Exception e) {
            logger.error("保存用户兴趣标签失败", e);
            throw new RuntimeException("保存用户兴趣标签失败", e);
        }
    }
} 