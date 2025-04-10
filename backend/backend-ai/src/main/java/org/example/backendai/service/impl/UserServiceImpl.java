package org.example.backendai.service.impl;

import java.util.Date;
import java.util.UUID;

import org.example.backendai.DTO.LoginRequest;
import org.example.backendai.DTO.LoginResponse;
import org.example.backendai.DTO.RegisterRequest;
import org.example.backendai.DTO.RegisterResponse;
import org.example.backendai.DTO.UpdateUserInfoRequest;
import org.example.backendai.entity.User;
import org.example.backendai.mapper.UserMapper;
import org.example.backendai.service.UserService;
import org.example.backendai.util.JwtUtil;
import org.example.backendai.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        log.info("开始登录，用户名: {}", loginRequest.getUsername());
        
        // 根据用户名查询用户
        User user = userMapper.findByUsername(loginRequest.getUsername());
        log.info("查询到的用户信息: {}", user);
        
        if (user == null) {
            log.warn("用户不存在: {}", loginRequest.getUsername());
            return LoginResponse.builder()
                    .error(1)
                    .msg("用户不存在")
                    .build();
        }
        
        // 验证密码
        boolean passwordMatch = PasswordUtil.matches(loginRequest.getPassword(), user.getPassword());
        log.info("密码验证结果: {}", passwordMatch);
        log.info("输入的密码: {}", loginRequest.getPassword());
        log.info("数据库中的密码: {}", user.getPassword());
        
        if (!passwordMatch) {
            log.warn("密码验证失败: {}", loginRequest.getUsername());
            return LoginResponse.builder()
                    .error(1)
                    .msg("用户名或密码错误")
                    .build();
        }
        
        // 生成JWT令牌
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        log.info("生成token成功: {}, 用户ID: {}", token, user.getId());
        
        // 调试生成的令牌
        jwtUtil.debugToken(token);
        
        // 构建并返回登录响应
        return LoginResponse.builder()
                .error(0)
                .msg("登录成功")
                .data(LoginResponse.LoginData.builder()
                        .token(token)
                        .username(user.getUsername())
                        .build())
                .build();
    }
    
    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {
        log.info("开始注册，用户名: {}", registerRequest.getUsername());
        
        // 校验用户名
        if (registerRequest.getUsername() == null || registerRequest.getUsername().trim().length() < 3) {
            log.warn("用户名不符合要求: {}", registerRequest.getUsername());
            return RegisterResponse.builder()
                    .error(1)
                    .msg("用户名长度不能小于3个字符")
                    .build();
        }
        
        // 校验密码
        if (registerRequest.getPassword() == null || registerRequest.getPassword().length() < 6) {
            log.warn("密码不符合要求");
            return RegisterResponse.builder()
                    .error(1)
                    .msg("密码长度不能小于6个字符")
                    .build();
        }
        
        // 校验两次密码是否一致
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            log.warn("两次密码不一致");
            return RegisterResponse.builder()
                    .error(1)
                    .msg("两次输入的密码不一致")
                    .build();
        }
        
        // 查询用户名是否已存在
        User existUser = userMapper.findByUsername(registerRequest.getUsername());
        if (existUser != null) {
            log.warn("用户名已存在: {}", registerRequest.getUsername());
            return RegisterResponse.builder()
                    .error(1)
                    .msg("用户名已存在")
                    .build();
        }
        
        // 加密密码
        String encryptedPassword = PasswordUtil.encode(registerRequest.getPassword());
        
        // 创建用户对象
        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(encryptedPassword)
                .role("USER")
                .status(1)
                .grade(registerRequest.getGrade())
                .major(registerRequest.getMajor())
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        
        try {
            // 保存用户
            int result = userMapper.saveUser(user);
            if (result > 0) {
                log.info("用户注册成功: {}", registerRequest.getUsername());
                return RegisterResponse.builder()
                        .error(0)
                        .msg("注册成功")
                        .data(RegisterResponse.RegisterData.builder()
                                .username(user.getUsername())
                                .grade(user.getGrade())
                                .major(user.getMajor())
                                .build())
                        .build();
            } else {
                log.error("用户注册失败: {}", registerRequest.getUsername());
                return RegisterResponse.builder()
                        .error(1)
                        .msg("注册失败")
                        .build();
            }
        } catch (Exception e) {
            log.error("用户注册异常", e);
            return RegisterResponse.builder()
                    .error(1)
                    .msg("注册失败: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
    
    @Override
    public boolean updateUserInfo(Long userId, UpdateUserInfoRequest updateUserInfoRequest) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            log.warn("更新用户信息失败：用户不存在 userId={}", userId);
            return false;
        }
        
        // 检查用户名是否已存在（如果更改了用户名）
        if (!user.getUsername().equals(updateUserInfoRequest.getUsername())) {
            User existingUser = userMapper.findByUsername(updateUserInfoRequest.getUsername());
            if (existingUser != null) {
                log.warn("更新用户信息失败：用户名已存在 username={}", updateUserInfoRequest.getUsername());
                return false;
            }
        }
        
        // 更新用户信息
        user.setUsername(updateUserInfoRequest.getUsername());
        user.setGrade(updateUserInfoRequest.getGrade());
        user.setMajor(updateUserInfoRequest.getMajor());
        
        int result = userMapper.updateUserInfo(user);
        if (result > 0) {
            log.info("用户信息更新成功: userId={}, username={}", userId, user.getUsername());
            return true;
        } else {
            log.error("用户信息更新失败: userId={}", userId);
            return false;
        }
    }
    
    @Override
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        // 检查用户是否存在
        User user = userMapper.findById(userId);
        if (user == null) {
            log.warn("更新密码失败：用户不存在 userId={}", userId);
            return false;
        }
        
        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("更新密码失败：旧密码不正确 userId={}", userId);
            return false;
        }
        
        // 加密新密码
        String encryptedPassword = passwordEncoder.encode(newPassword);
        
        // 更新密码
        int result = userMapper.updatePassword(userId, encryptedPassword);
        if (result > 0) {
            log.info("密码更新成功: userId={}", userId);
            return true;
        } else {
            log.error("密码更新失败: userId={}", userId);
            return false;
        }
    }
} 