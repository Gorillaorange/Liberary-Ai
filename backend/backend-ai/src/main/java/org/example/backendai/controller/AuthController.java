package org.example.backendai.controller;

import org.example.backendai.DTO.LoginRequest;
import org.example.backendai.DTO.LoginResponse;
import org.example.backendai.DTO.RegisterRequest;
import org.example.backendai.DTO.RegisterResponse;
import org.example.backendai.DTO.UpdatePasswordRequest;
import org.example.backendai.DTO.UpdateUserInfoRequest;
import org.example.backendai.entity.User;
import org.example.backendai.service.UserService;
import org.example.backendai.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 用户登录接口
     * 
     * @param loginRequest 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        return userService.login(loginRequest);
    }
    
    /**
     * 用户注册接口
     * 
     * @param registerRequest 注册请求
     * @return 注册响应
     */
    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest registerRequest) {
        return userService.register(registerRequest);
    }
    
    /**
     * 退出登录
     * 
     * @return 退出登录响应
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // 清除安全上下文
        SecurityContextHolder.clearContext();
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", 0);
        response.put("msg", "退出登录成功");
        response.put("action", "clear_token"); // 指示前端应该清除token
        response.put("redirect", "/login");    // 指示前端应重定向到登录页
        
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("Clear-Auth", "true") // 添加自定义响应头
                .body(response);
    }
    
    /**
     * 获取当前用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", 1, "msg", "未授权"));
        }
        
        User user = userService.findByUsername(jwtUtil.getUsernameFromToken(token.substring(7)));
        
        Map<String, Object> response = new HashMap<>();
        
        if (user != null) {
            response.put("error", 0);
            response.put("msg", "获取用户信息成功");
            
            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("role", user.getRole());
            data.put("grade", user.getGrade());
            data.put("major", user.getMajor());
            
            response.put("data", data);
        } else {
            response.put("error", 1);
            response.put("msg", "获取用户信息失败");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 更新用户基本信息
     * 
     * @param token 授权token
     * @param request 更新用户信息请求
     * @return 更新结果
     */
    @PutMapping("/user-info")
    public ResponseEntity<Map<String, Object>> updateUserInfo(
            @RequestHeader("Authorization") String token,
            @RequestBody UpdateUserInfoRequest request) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", 1, "msg", "未授权"));
        }
        
        boolean success = userService.updateUserInfo(userId, request);
        
        if (success) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", 0);
            response.put("msg", "用户信息更新成功");
            
            // 获取更新后的用户信息
            User user = userService.findByUsername(request.getUsername());
            if (user != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("username", user.getUsername());
                data.put("grade", user.getGrade());
                data.put("major", user.getMajor());
                
                response.put("data", data);
            }
            
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(400).body(Map.of(
                "error", 1, 
                "msg", "用户信息更新失败，请确保用户名未被占用"
            ));
        }
    }
    
    /**
     * 更新用户密码
     * 
     * @param token 授权token
     * @param request 更新密码请求
     * @return 更新结果
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody UpdatePasswordRequest request) {
        
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", 1, "msg", "未授权"));
        }
        
        boolean success = userService.updatePassword(userId, request.getOldPassword(), request.getNewPassword());
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "error", 0,
                "msg", "密码更新成功"
            ));
        } else {
            return ResponseEntity.status(400).body(Map.of(
                "error", 1,
                "msg", "密码更新失败，请确保旧密码正确"
            ));
        }
    }
    
    /**
     * 从授权头获取用户ID
     */
    private Long getUserIdFromToken(String bearerToken) {
        try {
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);
                return jwtUtil.getUserIdFromToken(token);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
} 