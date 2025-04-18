package org.example.backendai.controller;

import org.example.backendai.DTO.BookDTO;
import org.example.backendai.service.BookRecommendationService;
import org.example.backendai.service.BookRecommendationHistoryService;
import org.example.backendai.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图书推荐控制器
 */
@RestController
@RequestMapping("/api/books")
public class BookRecommendationController {

    private static final Logger logger = LoggerFactory.getLogger(BookRecommendationController.class);
    
    @Autowired
    private BookRecommendationService bookRecommendationService;
    
    @Autowired
    private BookRecommendationHistoryService recommendationHistoryService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 获取针对用户兴趣标签的图书推荐
     *
     * @param authHeader 认证头
     * @param limit 推荐数量限制，默认为5
     * @param shouldGenerateNewProfile 是否生成新的用户画像，默认为false
     * @return 推荐图书列表
     */
    @GetMapping("/recommend-books")
    public ResponseEntity<?> getRecommendedBooks(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "false") boolean shouldGenerateNewProfile) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.getUserIdFromToken(token);
            
            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "无效的用户凭证"));
            }
            
            List<BookDTO> recommendations = bookRecommendationService.recommendBooksByUserInterests(
                    userId, limit, shouldGenerateNewProfile);
            
            Map<String, Object> response = new HashMap<>();
            response.put("recommendations", recommendations);
            response.put("generatedByAI", shouldGenerateNewProfile);
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取图书推荐时出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "获取推荐失败: " + e.getMessage()));
        }
    }
    
    /**
     * 记录用户点击了推荐的图书
     *
     * @param authHeader 认证头
     * @param bookId 图书ID
     * @return 操作结果
     */
    @PostMapping("/recommendations/{bookId}/click")
    public ResponseEntity<?> trackRecommendationClick(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long bookId) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.getUserIdFromToken(token);
            
            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "无效的用户凭证"));
            }
            
            boolean updated = recommendationHistoryService.updateClickStatus(userId, bookId);
            
            if (updated) {
                return ResponseEntity.ok(Map.of("success", true, "message", "点击记录已更新"));
            } else {
                return ResponseEntity.ok(Map.of("success", false, "message", "未找到匹配的推荐记录"));
            }
        } catch (Exception e) {
            logger.error("更新点击状态时出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "更新点击状态失败: " + e.getMessage()));
        }
    }
    
    /**
     * 清理过期的推荐记录
     *
     * @param authHeader 认证头
     * @param days 超过多少天视为过期，默认30天
     * @return 操作结果
     */
    @DeleteMapping("/recommendations/expired")
    public ResponseEntity<?> cleanExpiredRecommendations(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "30") int days) {
        
        try {
            // 验证管理员权限
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.getUserIdFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);
            
            if (userId == null || !"ADMIN".equals(role)) {
                return ResponseEntity.badRequest().body(Map.of("error", "需要管理员权限"));
            }
            
            int cleaned = recommendationHistoryService.cleanExpiredRecommendations(days);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "已清理过期推荐记录", 
                "cleaned", cleaned
            ));
        } catch (Exception e) {
            logger.error("清理过期推荐记录时出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "清理操作失败: " + e.getMessage()));
        }
    }
} 