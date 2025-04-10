package org.example.backendai.controller;

import lombok.RequiredArgsConstructor;
import org.example.backendai.entity.Book;
import org.example.backendai.entity.UserBookInterest;
import org.example.backendai.entity.UserQuestionTopic;
import org.example.backendai.service.UserProfileService;
import org.example.backendai.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天分析控制器
 * 处理用户聊天内容，提取个性化信息
 */
@RestController
@RequestMapping("/api/chat/analysis")
@RequiredArgsConstructor
public class ChatAnalysisController {

    private final UserProfileService userProfileService;
    private final JwtUtil jwtUtil;
    private static final Logger logger = LoggerFactory.getLogger(ChatAnalysisController.class);

    /**
     * 处理聊天内容，提取和存储个性化信息
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processChat(
            @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            // 从JWT获取用户ID
            Long userId = getUserIdFromToken(authHeader);
            
            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "无法获取用户ID"
                ));
            }
            
            // 获取请求参数
            String message = (String) request.get("message");
            String topic = (String) request.getOrDefault("topic", "");
            
            // 处理聊天内容，提取个性化信息
            userProfileService.processUserQuestion(userId, message, topic);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "聊天内容已处理"
            ));
            
        } catch (Exception e) {
            logger.error("处理聊天内容失败: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "处理失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取用户感兴趣的书籍
     */
    @GetMapping("/user/books")
    public ResponseEntity<Map<String, Object>> getUserInterestedBooks(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            // 从JWT获取用户ID
            Long userId = getUserIdFromToken(authHeader);
            
            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "无法获取用户ID"
                ));
            }
            
            List<Book> books = userProfileService.getUserInterestedBooks(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", books);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取用户感兴趣的书籍失败: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "获取失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取用户常见问题主题
     */
    @GetMapping("/user/topics")
    public ResponseEntity<Map<String, Object>> getUserTopics(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            // 从JWT获取用户ID
            Long userId = getUserIdFromToken(authHeader);
            
            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "无法获取用户ID"
                ));
            }
            
            List<UserQuestionTopic> topics = userProfileService.getUserTopQuestions(userId, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", topics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取用户问题主题失败: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "获取失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 搜索书籍
     */
    @GetMapping("/books/search")
    public ResponseEntity<Map<String, Object>> searchBooks(@RequestParam String keyword) {
        try {
            List<Book> books = userProfileService.searchBooks(keyword);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", books);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("搜索书籍失败: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "搜索失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取用户对特定书籍的兴趣详情
     */
    @GetMapping("/user/book-interest/{bookId}")
    public ResponseEntity<Map<String, Object>> getUserBookInterest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookId) {
        
        try {
            // 从JWT获取用户ID
            Long userId = getUserIdFromToken(authHeader);
            
            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "无法获取用户ID"
                ));
            }
            
            UserBookInterest interest = userProfileService.getUserBookInterest(userId, bookId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", interest);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取用户书籍兴趣详情失败: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "获取失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 清空用户个性化信息
     */
    @DeleteMapping("/user/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearUserProfile(@RequestParam Long userId) {
        try {
            userProfileService.clearUserProfile(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "用户个性化信息已清空"
            ));
            
        } catch (Exception e) {
            logger.error("清空用户个性化信息失败: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "清空失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 从授权头获取用户ID
     */
    private Long getUserIdFromToken(String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return jwtUtil.getUserIdFromToken(token);
            }
            return null;
        } catch (Exception e) {
            logger.error("解析token失败: ", e);
            return null;
        }
    }
} 