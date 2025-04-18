package org.example.backendai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backendai.DTO.BookDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图书推荐服务，基于向量搜索和标签匹配实现
 */
@Service
@SuppressWarnings("unchecked")
public class BookRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(BookRecommendationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private UserInterestService userInterestService;
    
    @Autowired
    private AIApiService aiApiService;
    
    @Autowired
    private BookRecommendationHistoryService recommendationHistoryService;


    // 构造函数注入
    public BookRecommendationService(
            JdbcTemplate jdbcTemplate,
            UserInterestService userInterestService,
            AIApiService aiApiService,
            BookRecommendationHistoryService recommendationHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userInterestService = userInterestService;
        this.aiApiService = aiApiService;
        this.recommendationHistoryService = recommendationHistoryService;
    }

    /**
     * 基于用户兴趣标签推荐图书
     * 
     * @param userId 用户ID
     * @param limit 返回推荐书籍数量限制
     * @param shouldGenerateNewProfile 是否使用AI重新生成推荐
     * @return 推荐图书列表
     */
    public List<BookDTO> recommendBooksByUserInterests(Long userId, int limit, boolean shouldGenerateNewProfile) {
        try {
            // 1. 获取用户兴趣标签
            List<String> userInterests = userInterestService.getUserInterests(userId);
            if (userInterests.isEmpty()) {
                logger.info("用户 {} 没有兴趣标签，返回随机推荐", userId);
                return getRandomBooks(limit);
            }
            
            // 2. 根据shouldGenerateNewProfile决定推荐策略
            if (shouldGenerateNewProfile) {
                // 使用AI生成新的推荐
                return generateNewRecommendations(userId, userInterests, limit);
            } else {
                // 优先使用历史推荐
                return getRecommendationsFromHistory(userId, userInterests, limit);
            }
        } catch (Exception e) {
            logger.error("推荐图书时发生错误: {}", e.getMessage(), e);
            return getRandomBooks(limit);
        }
    }

    /**
     * 生成新的推荐
     */
    private List<BookDTO> generateNewRecommendations(Long userId, List<String> interests, int limit) {
        try {
            // 1. 构建用户兴趣描述
            String userProfile = buildUserProfile(interests);
            logger.info("用户兴趣描述: {}", userProfile);

            // 2. 调用向量搜索API
            Map<String, Object> searchResponse = aiApiService.searchSimilarBooks(userProfile, limit);
            
            if (searchResponse.containsKey("error")) {
                logger.error("向量搜索失败: {}", searchResponse.get("error"));
                return getRandomBooks(limit);
            }

            // 3. 处理搜索结果
            List<Map<String, Object>> hits = (List<Map<String, Object>>) searchResponse.get("hits");
            if (hits == null || hits.isEmpty()) {
                logger.warn("未找到匹配的图书，返回随机推荐");
                return getRandomBooks(limit);
            }
            
            logger.info("获取到{}条搜索结果", hits.size());

            // 4. 转换结果
            List<BookDTO> recommendations = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                try {
                    logger.debug("处理搜索结果: {}", hit);
                    
                    // 打印关键字段
                    logger.info("处理book_id={}, score={}", hit.get("book_id"), hit.get("score"));
                    
                    String text = hit.get("text") != null ? hit.get("text").toString() : "";
                    String title = extractTitle(text);
                    String description = extractDescription(text);
                    
                    logger.info("提取标题: {}", title);
                    logger.info("提取描述长度: {}", description.length());
                    
                    BookDTO book = new BookDTO();
                    book.setId(Long.parseLong(hit.get("book_id").toString()));
                    book.setTitle(title);
                    book.setDescription(description);
                    
                    // 处理标签
                    Object metadataObj = hit.get("metadata");
                    List<String> tags = extractTags(metadataObj);
                    logger.info("提取标签: {}", tags);
                    book.setTags(tags);
                    
                    // 设置相似度分数
                    book.setSimilarity(((Number) hit.get("score")).doubleValue());
                    
                    recommendations.add(book);
                    logger.info("成功添加图书: {}", book.getTitle());
                } catch (Exception e) {
                    logger.error("处理搜索结果时出错: {}", e.getMessage(), e);
                }
            }

            if (recommendations.isEmpty()) {
                logger.warn("无法解析搜索结果，返回随机推荐");
                return getRandomBooks(limit);
            }
            
            logger.info("成功生成{}条推荐", recommendations.size());
            
            // 保存推荐历史
            recommendationHistoryService.saveRecommendationHistory(userId, recommendations, "AI");
            
            return recommendations;
        } catch (Exception e) {
            logger.error("生成推荐时出错: {}", e.getMessage(), e);
            return getRandomBooks(limit);
        }
    }

    /**
     * 从文本中提取标题
     */
    private String extractTitle(String text) {
        if (text == null || text.isEmpty()) {
            logger.warn("提取标题的文本为空");
            return "未知标题";
        }
        
        try {
            int titleStart = text.indexOf("书名: ");
            if (titleStart != -1) {
                int titleEnd = text.indexOf("\n", titleStart);
                if (titleEnd != -1) {
                    String title = text.substring(titleStart + 4, titleEnd).trim();
                    // 检查原作名前缀
                    int origTitleStart = title.indexOf("原作名: ");
                    if (origTitleStart != -1) {
                        title = title.substring(0, origTitleStart).trim();
                    }
                    return title;
                } else {
                    return text.substring(titleStart + 4).trim();
                }
            } else {
                // 如果没有找到"书名:"，则尝试直接使用第一行
                int firstLineEnd = text.indexOf("\n");
                if (firstLineEnd != -1) {
                    return text.substring(0, firstLineEnd).trim();
                }
            }
            logger.warn("无法从文本中提取标题: {}", text.substring(0, Math.min(50, text.length())));
            return "未知标题";
        } catch (Exception e) {
            logger.error("提取标题时出错: {}", e.getMessage());
            return "未知标题";
        }
    }

    /**
     * 从文本中提取描述
     */
    private String extractDescription(String text) {
        if (text == null || text.isEmpty()) {
            logger.warn("提取描述的文本为空");
            return "暂无描述";
        }
        
        try {
            int descStart = text.indexOf("内容简介: ");
            if (descStart != -1) {
                // 查找下一个段落的开始（通常是"作者信息:"或者"分类:"）
                int authorInfoStart = text.indexOf("作者信息:", descStart);
                int categoryStart = text.indexOf("分类:", descStart);
                
                int descEnd;
                if (authorInfoStart != -1 && (categoryStart == -1 || authorInfoStart < categoryStart)) {
                    descEnd = authorInfoStart;
                } else if (categoryStart != -1) {
                    descEnd = categoryStart;
                } else {
                    // 如果找不到后续段落，则使用全部内容
                    descEnd = text.length();
                }
                
                String description = text.substring(descStart + 5, descEnd).trim();
                // 删除可能存在的"(展开全部)"标记
                description = description.replace("...(展开全部)", "");
                return description;
            } else {
                // 如果没有找到"内容简介:"，则尝试使用第二段
                int firstLineEnd = text.indexOf("\n");
                if (firstLineEnd != -1 && firstLineEnd + 1 < text.length()) {
                    int secondLineEnd = text.indexOf("\n", firstLineEnd + 1);
                    if (secondLineEnd != -1) {
                        return text.substring(firstLineEnd + 1, secondLineEnd).trim();
                    } else {
                        return text.substring(firstLineEnd + 1).trim();
                    }
                }
            }
            logger.warn("无法从文本中提取描述: {}", text.substring(0, Math.min(50, text.length())));
            return "暂无描述";
        } catch (Exception e) {
            logger.error("提取描述时出错: {}", e.getMessage());
            return "暂无描述";
        }
    }

    /**
     * 从元数据中提取标签
     */
    private List<String> extractTags(Object metadata) {
        List<String> tags = new ArrayList<>();
        if (metadata == null) {
            logger.warn("元数据为空，无法提取标签");
            return tags;
        }
        
        try {
            if (metadata instanceof Map) {
                Map<String, Object> metaMap = (Map<String, Object>) metadata;
                logger.debug("元数据内容: {}", metaMap);
                
                Object tagsObj = metaMap.get("tags");
                if (tagsObj instanceof List) {
                    tags = (List<String>) tagsObj;
                    logger.debug("从元数据中提取到标签: {}", tags);
                } else if (tagsObj instanceof String) {
                    // 如果标签是字符串格式，尝试解析为列表
                    String tagsStr = (String) tagsObj;
                    if (tagsStr.startsWith("[") && tagsStr.endsWith("]")) {
                        tagsStr = tagsStr.substring(1, tagsStr.length() - 1);
                        String[] tagArray = tagsStr.split(",");
                        for (String tag : tagArray) {
                            tags.add(tag.trim().replace("\"", ""));
                        }
                    } else {
                        tags.add(tagsStr);
                    }
                    logger.debug("从字符串元数据中提取到标签: {}", tags);
                }
                
                // 如果标签仍为空，尝试从category获取
                if (tags.isEmpty() && metaMap.containsKey("category")) {
                    Object category = metaMap.get("category");
                    if (category != null && !category.toString().isEmpty() && !"null".equals(category.toString())) {
                        tags.add(category.toString());
                        logger.debug("从category中提取到标签: {}", category);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("提取标签时出错: {}", e.getMessage());
        }
        
        return tags;
    }

    /**
     * 从历史记录获取推荐
     */
    private List<BookDTO> getRecommendationsFromHistory(Long userId, List<String> interests, int limit) {
        try {
            // 1. 获取历史推荐记录
            List<Map<String, Object>> recentRecommendations = recommendationHistoryService.getRecentRecommendations(userId, limit);
            
            if (!recentRecommendations.isEmpty()) {
                logger.info("使用历史推荐记录");
                List<Integer> bookIds = recommendationHistoryService.extractBookIdsFromRecommendations(recentRecommendations);
                return getBookDetails(bookIds);
            }
            
            // 2. 如果没有历史记录，使用标签匹配
            logger.info("无历史记录，使用标签匹配");
            return getRecommendationsByTags(userId, interests, limit);
        } catch (Exception e) {
            logger.error("获取历史推荐时发生错误: {}", e.getMessage(), e);
            return getRandomBooks(limit);
        }
    }

    /**
     * 构建用户兴趣描述
     */
    private String buildUserProfile(List<String> interests) {
        return String.format(
            "用户对以下主题感兴趣：%s。请推荐相关的图书。",
            String.join("、", interests)
        );
    }

    /**
     * 将搜索结果转换为BookDTO
     */
    private List<BookDTO> convertToBookDTOs(List<Map<String, Object>> searchResults) {
        return searchResults.stream()
            .map(result -> {
                BookDTO book = new BookDTO();
                book.setId(((Number) result.get("book_id")).longValue());
                book.setTitle((String) result.get("title"));
                book.setDescription((String) result.get("description"));
                book.setSimilarity(((Number) result.get("similarity")).doubleValue());
                
                // 设置其他属性
                if (result.containsKey("tags")) {
                    try {
                        String tagsJson = (String) result.get("tags");
                        List<String> tagsList = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
                        book.setTags(tagsList);
                    } catch (Exception e) {
                        logger.warn("解析标签时出错", e);
                    }
                }
                
                return book;
            })
            .collect(Collectors.toList());
    }

    /**
     * 根据标签匹配图书
     */
    private List<BookDTO> getRecommendationsByTags(Long userId, List<String> interests, int limit) {
        if (interests.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            // 构建SQL查询
            String sql = "SELECT * FROM tushu WHERE " + 
                interests.stream()
                    .map(tag -> "tags LIKE ?")
                    .collect(Collectors.joining(" OR ")) +
                " LIMIT ?";
            
            // 准备参数
            Object[] params = new Object[interests.size() + 1];
            for (int i = 0; i < interests.size(); i++) {
                params[i] = "%" + interests.get(i) + "%";
            }
            params[interests.size()] = limit;
            
            // 执行查询
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            
            // 转换为BookDTO
            List<BookDTO> books = rows.stream()
                .map(row -> {
                    BookDTO book = new BookDTO();
                    book.setId(((Number) row.get("id")).longValue());
                    book.setTitle((String) row.get("title"));
                    book.setDescription((String) row.get("neirong_jianjie"));
                    book.setSimilarity(0.8);  // 标签匹配的默认相似度
                    
                    // 设置其他属性
                    if (row.get("tags") != null) {
                        try {
                            String tagsJson = (String) row.get("tags");
                            List<String> tagsList = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
                            book.setTags(tagsList);
                        } catch (Exception e) {
                            logger.warn("解析标签时出错", e);
                        }
                    }
                    
                    return book;
                })
                .collect(Collectors.toList());
            
            // 保存推荐历史
            recommendationHistoryService.saveRecommendationHistory(userId, books, "TAG");
            
            return books;
        } catch (Exception e) {
            logger.error("根据标签匹配图书时出错: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取随机图书
     */
    private List<BookDTO> getRandomBooks(int limit) {
        try {
            String sql = "SELECT * FROM tushu ORDER BY RAND() LIMIT ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, limit);
            
            return rows.stream()
                .map(row -> {
                    BookDTO book = new BookDTO();
                    book.setId(((Number) row.get("id")).longValue());
                    book.setTitle((String) row.get("title"));
                    book.setDescription((String) row.get("neirong_jianjie"));
                    book.setSimilarity(0.5);  // 随机推荐的默认相似度
                    
                    // 设置其他属性
                    if (row.get("tags") != null) {
                        try {
                            String tagsJson = (String) row.get("tags");
                            List<String> tagsList = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
                            book.setTags(tagsList);
                        } catch (Exception e) {
                            logger.warn("解析标签时出错", e);
                        }
                    }
                    
                    return book;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取随机图书时发生错误: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取图书详细信息
     */
    private List<BookDTO> getBookDetails(List<Integer> bookIds) {
        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String inClause = bookIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            String sql = "SELECT * FROM tushu WHERE id IN (" + inClause + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            
            return rows.stream()
                .map(row -> {
                    BookDTO book = new BookDTO();
                    book.setId(((Number) row.get("id")).longValue());
                    book.setTitle((String) row.get("title"));
                    book.setDescription((String) row.get("neirong_jianjie"));
                    
                    // 设置其他属性
                    if (row.get("tags") != null) {
                        try {
                            String tagsJson = (String) row.get("tags");
                            List<String> tagsList = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
                            book.setTags(tagsList);
                        } catch (Exception e) {
                            logger.warn("解析标签时出错", e);
                        }
                    }
                    
                    return book;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取图书详细信息出错: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
} 