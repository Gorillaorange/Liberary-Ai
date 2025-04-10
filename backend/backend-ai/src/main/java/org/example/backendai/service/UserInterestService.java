package org.example.backendai.service;

import org.example.backendai.entity.UserInterestTag;
import org.example.backendai.mapper.UserInterestTagMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户兴趣标签服务类
 */
@Service
public class UserInterestService {

    private static final Logger logger = LoggerFactory.getLogger(UserInterestService.class);
    
    @Autowired
    private UserInterestTagMapper userInterestTagMapper;
    
    /**
     * 获取用户的所有兴趣标签
     * 
     * @param userId 用户ID
     * @return 兴趣标签列表
     */
    public List<String> getUserInterests(Long userId) {
        try {
            if (userId == null) {
                return Collections.emptyList();
            }
            
            List<String> tags = userInterestTagMapper.findTopTagsByUserId(userId, 10);
            
            if (tags == null) {
                logger.warn("用户[{}]的兴趣标签查询结果为null", userId);
                return Collections.emptyList();
            }
            
            // 过滤掉null和空字符串
            return tags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("获取用户[{}]兴趣标签失败: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 保存用户兴趣标签（增量模式）
     * 
     * @param userId 用户ID
     * @param interests 兴趣标签列表
     * @return 保存成功的标签数量
     */
    @Transactional
    public int saveUserInterests(Long userId, List<String> interests) {
        if (userId == null || interests == null || interests.isEmpty()) {
            logger.warn("保存用户兴趣标签 - 参数无效: userId={}, interests={}", userId, interests);
            return 0;
        }
        
        logger.info("保存用户兴趣标签: userId={}, interests={}", userId, interests);
        
        int savedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        
        // 处理每个标签
        for (String tagName : interests) {
            // 标准化标签名（去除空格、转小写等）
            String normalizedTag = normalizeTagName(tagName);
            if (normalizedTag.isEmpty()) {
                continue;
            }
            
            try {
                // 查找是否已存在该标签
                UserInterestTag existingTag = userInterestTagMapper.findByUserIdAndTagName(userId, normalizedTag);
                
                if (existingTag != null) {
                    // 增量更新已存在的标签，确保所有字段都有值
                    int currentCount = existingTag.getOccurrenceCount() != null ? existingTag.getOccurrenceCount() : 0;
                    existingTag.setOccurrenceCount(currentCount + 1);
                    existingTag.setWeight(calculateWeight(currentCount + 1));
                    existingTag.setLastOccurrenceTime(now);
                    
                    // 确保其他字段也有值
                    if (existingTag.getFirstOccurrenceTime() == null) {
                        existingTag.setFirstOccurrenceTime(now);
                    }
                    
                    userInterestTagMapper.update(existingTag);
                } else {
                    // 创建新标签，确保所有必要字段都有值
                    UserInterestTag newTag = UserInterestTag.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .tagName(normalizedTag)
                        .weight(10) // 初始权重
                        .occurrenceCount(1)
                        .firstOccurrenceTime(now)
                        .lastOccurrenceTime(now)
                        .build();
                    
                    // 再次检查必要字段是否为null
                    if (newTag.getOccurrenceCount() == null) newTag.setOccurrenceCount(1);
                    if (newTag.getWeight() == null) newTag.setWeight(10);
                    
                    userInterestTagMapper.insert(newTag);
                }
                
                savedCount++;
            } catch (Exception e) {
                logger.error("保存标签失败: " + normalizedTag, e);
            }
        }
        
        logger.info("成功保存标签数量: {}/{}", savedCount, interests.size());
        return savedCount;
    }
    
    /**
     * 获取用户的热门兴趣标签
     * 
     * @param userId 用户ID
     * @param limit 最大数量
     * @return 标签列表
     */
    public List<String> getUserTopInterests(Long userId, int limit) {
        try {
            if (userId == null) {
                return new java.util.ArrayList<>();
            }
            

            List<String> tags = userInterestTagMapper.findTopTagsByUserId(userId, limit);
            
            if (tags == null) {
                logger.warn("用户[{}]的兴趣标签查询结果为null", userId);
                return new java.util.ArrayList<>();
            }
            // 过滤掉null和空字符串
            List<String> result = tags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .map(String::trim)
                .collect(Collectors.toList());

            return result;
        } catch (Exception e) {
            logger.error("获取用户[{}]热门兴趣标签失败: {}", userId, e.getMessage(), e);
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * 标准化标签名称
     */
    private String normalizeTagName(String tagName) {
        if (tagName == null) return "";
        
        // 移除引号和其他可能干扰的特殊字符
        return tagName.trim()
                .replaceAll("[\"']", "") // 移除引号
                .replaceAll("[\\[\\]{}()]", "") // 移除括号
                .trim();
    }
    
    /**
     * 根据出现次数计算权重
     */
    private int calculateWeight(Integer occurrenceCount) {
        // 确保不是null，且至少为1
        int count = occurrenceCount != null ? Math.max(1, occurrenceCount) : 1;
        
        // 简单的权重计算公式，可以根据需要调整
        return Math.min(100, 10 + count * 5);
    }
    
    /**
     * 删除用户的所有兴趣标签
     * 
     * @param userId 用户ID
     * @return 删除的标签数量
     */
    @Transactional
    public int deleteUserInterests(Long userId) {
        if (userId == null) {
            logger.warn("删除用户兴趣标签 - 用户ID为空");
            return 0;
        }
        
        try {
            int count = userInterestTagMapper.deleteByUserId(userId);
            logger.info("已删除用户[{}]的所有兴趣标签，共{}个", userId, count);
            return count;
        } catch (Exception e) {
            logger.error("删除用户兴趣标签失败", e);
            throw new RuntimeException("删除用户兴趣标签失败", e);
        }
    }
} 