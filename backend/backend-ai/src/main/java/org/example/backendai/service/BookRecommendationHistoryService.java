package org.example.backendai.service;

import org.example.backendai.DTO.BookDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图书推荐历史记录服务
 */
@Service
public class BookRecommendationHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(BookRecommendationHistoryService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 保存推荐历史记录
     *
     * @param userId 用户ID
     * @param books 推荐的图书列表
     * @param source 推荐来源
     * @return 保存成功的记录数
     */
    public int saveRecommendationHistory(Long userId, List<BookDTO> books, String source) {
        if (userId == null || books == null || books.isEmpty()) {
            return 0;
        }

        int savedCount = 0;
        for (BookDTO book : books) {
            try {
                // 检查是否已存在相同推荐
                String checkSql = "SELECT COUNT(*) FROM book_recommendation_history WHERE user_id = ? AND book_id = ?";
                int count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, book.getId());

                if (count > 0) {
                    // 更新现有记录
                    String updateSql = "UPDATE book_recommendation_history SET similarity_score = ?, recommendation_source = ?, recommendation_time = NOW() WHERE user_id = ? AND book_id = ?";
                    jdbcTemplate.update(updateSql, 
                        book.getSimilarity(), 
                        source, 
                        userId, 
                        book.getId());
                } else {
                    // 插入新记录
                    String insertSql = "INSERT INTO book_recommendation_history (user_id, book_id, similarity_score, recommendation_source, recommendation_time) VALUES (?, ?, ?, ?, NOW())";
                    jdbcTemplate.update(insertSql, 
                        userId, 
                        book.getId(),
                        book.getSimilarity(),
                        source);
                }
                savedCount++;
            } catch (Exception e) {
                logger.error("保存推荐历史记录失败: {}", e.getMessage(), e);
            }
        }
        return savedCount;
    }

    /**
     * 获取用户最近的推荐历史记录
     *
     * @param userId 用户ID
     * @param limit 返回记录数量限制
     * @return 推荐历史记录
     */
    public List<Map<String, Object>> getRecentRecommendations(Long userId, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }

        try {
            String sql = "SELECT * FROM book_recommendation_history WHERE user_id = ? ORDER BY recommendation_time DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, userId, limit);
        } catch (Exception e) {
            logger.error("获取推荐历史记录失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 更新推荐记录的点击状态
     *
     * @param userId 用户ID
     * @param bookId 图书ID
     * @return 是否更新成功
     */
    public boolean updateClickStatus(Long userId, Long bookId) {
        if (userId == null || bookId == null) {
            return false;
        }

        try {
            String sql = "UPDATE book_recommendation_history SET is_clicked = 1 WHERE user_id = ? AND book_id = ?";
            int rows = jdbcTemplate.update(sql, userId, bookId);
            return rows > 0;
        } catch (Exception e) {
            logger.error("更新点击状态失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清除过期的推荐记录
     *
     * @param days 超过多少天视为过期
     * @return 清除的记录数
     */
    public int cleanExpiredRecommendations(int days) {
        try {
            String sql = "DELETE FROM book_recommendation_history WHERE recommendation_time < DATE_SUB(NOW(), INTERVAL ? DAY)";
            return jdbcTemplate.update(sql, days);
        } catch (Exception e) {
            logger.error("清除过期推荐记录失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 获取推荐记录中的图书ID列表
     *
     * @param recommendations 推荐记录列表
     * @return 图书ID列表
     */
    public List<Integer> extractBookIdsFromRecommendations(List<Map<String, Object>> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return Collections.emptyList();
        }

        return recommendations.stream()
            .map(rec -> ((Number) rec.get("book_id")).intValue())
            .collect(Collectors.toList());
    }
} 