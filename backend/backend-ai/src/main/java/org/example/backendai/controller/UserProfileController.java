package org.example.backendai.controller;

import org.example.backendai.service.ChatMessageService;
import org.example.backendai.service.UserProfileService;
import org.example.backendai.service.AIApiService;
import org.example.backendai.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.example.backendai.service.UserInterestService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户画像控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

    private final UserProfileService userProfileService;
    private final AIApiService aiApiService;
    private final JwtUtil jwtUtil;
    private final UserInterestService userInterestService;
    private final ChatMessageService chatMessageService;

    @Autowired
    public UserProfileController(UserProfileService userProfileService, 
                                AIApiService aiApiService, 
                                JwtUtil jwtUtil,
                                UserInterestService userInterestService,
                                ChatMessageService chatMessageService) {
        this.userProfileService = userProfileService;
        this.aiApiService = aiApiService;
        this.jwtUtil = jwtUtil;
        this.userInterestService = userInterestService;
        this.chatMessageService = chatMessageService;
    }

    /**
     * 生成用户画像
     * 根据用户的所有聊天记录，使用AI分析生成用户兴趣标签
     *
     * @param token 用户认证token
     * @return 操作结果
     */
    @PostMapping("/generate-profile")
    public ResponseEntity<?> generateUserProfile(@RequestHeader("Authorization") String token) {
        try {
            // 从token中获取用户ID
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            if (userId == null) {
                return ResponseEntity.status(401).body("无效的用户认证");
            }
            
            logger.info("开始为用户ID: {} 生成兴趣画像", userId);
            
            // 获取用户的所有聊天记录
            List<Map<String, Object>> allUserMessages = chatMessageService.getAllUserMessages(userId);
            
            if (allUserMessages.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "没有足够的聊天记录来生成画像"));
            }
            
            // 提取所有聊天内容
            StringBuilder chatContent = new StringBuilder();
            for (Map<String, Object> message : allUserMessages) {
                // 只考虑用户发送的消息
                if ("user".equals(message.get("role"))) {
                    chatContent.append(message.get("content")).append("\n");
                }
            }
            
            // 使用AI服务分析用户兴趣
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", userId);
            requestBody.put("chatContent", chatContent.toString());
            
            Map<String, Object> aiResponse = aiApiService.analyzeUserInterests(requestBody);
            
            // 保存用户兴趣标签
            if (aiResponse != null && aiResponse.containsKey("interests")) {
                @SuppressWarnings("unchecked")
                List<String> interests = (List<String>) aiResponse.get("interests");
                
                // 检查interests是否为空列表
                if (interests == null || interests.isEmpty()) {
                    // 如果是空列表，直接返回消息
                    String message = aiResponse.containsKey("message") ? 
                        (String) aiResponse.get("message") : 
                        "目前还无兴趣，多与助手聊天吧";
                    
                    return ResponseEntity.ok(Map.of(
                        "success", true, 
                        "message", message,
                        "interests", interests != null ? interests : List.of()
                    ));
                } else {
                    // 有兴趣标签，保存并返回
                    userProfileService.saveUserInterests(userId, interests);
                    
                    return ResponseEntity.ok(Map.of(
                        "success", true, 
                        "message", "用户画像生成成功", 
                        "interests", interests
                    ));
                }
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false, 
                    "message", "AI分析未能产生有效结果",
                    "interests", List.of()
                ));
            }
            
        } catch (Exception e) {
            logger.error("生成用户画像时出错", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 获取用户兴趣标签
     */
    @GetMapping("/interest-tags")
    public ResponseEntity<Map<String, Object>> getUserInterestTags(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            // 从JWT获取用户ID
            Long userId = jwtUtil.getUserIdFromToken(authHeader);
            
            if (userId == null) {
                logger.warn("获取用户兴趣标签 - 无法获取用户ID，认证信息: {}", authHeader);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "无法获取用户ID"
                ));
            }

            
            // 获取用户的兴趣标签（最多10个）
            List<String> tags = userInterestService.getUserTopInterests(userId, 10);
            
            // 检查返回的标签是否为空
            if (tags == null || tags.isEmpty()) {
                logger.info("用户[{}]的兴趣标签为空", userId);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", new ArrayList<String>(),
                    "message", "暂无兴趣标签数据"
                ));
            }
            
            // 再次检查tags列表中是否有null或空字符串
            List<String> validTags = tags.stream()
                    .filter(tag -> tag != null && !tag.trim().isEmpty())
                    .collect(Collectors.toList());
            
            if (validTags.size() < tags.size()) {
                logger.warn("过滤后的标签数量减少: {} -> {}", tags.size(), validTags.size());
            }
            
            logger.info("返回用户[{}]的有效兴趣标签: {}", userId, validTags);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", validTags,
                "message", "获取成功"
            ));
            
        } catch (Exception e) {
            logger.error("获取用户兴趣标签失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取用户兴趣标签失败: " + e.getMessage()
            ));
        }
    }
} 