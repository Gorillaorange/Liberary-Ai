package org.example.backendai.service;

import org.example.backendai.DTO.LoginRequest;
import org.example.backendai.DTO.LoginResponse;
import org.example.backendai.DTO.RegisterRequest;
import org.example.backendai.DTO.RegisterResponse;
import org.example.backendai.DTO.UpdateUserInfoRequest;
import org.example.backendai.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {
    
    /**
     * 用户登录
     * 
     * @param loginRequest 登录请求
     * @return 登录响应
     */
    LoginResponse login(LoginRequest loginRequest);
    
    /**
     * 用户注册
     * 
     * @param registerRequest 注册请求
     * @return 注册响应
     */
    RegisterResponse register(RegisterRequest registerRequest);
    
    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户实体
     */
    User findByUsername(String username);
    
    /**
     * 更新用户基本信息
     * 
     * @param userId 用户ID
     * @param updateUserInfoRequest 更新用户信息请求
     * @return 更新是否成功
     */
    boolean updateUserInfo(Long userId, UpdateUserInfoRequest updateUserInfoRequest);
    
    /**
     * 更新用户密码
     * 
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 更新是否成功
     */
    boolean updatePassword(Long userId, String oldPassword, String newPassword);
} 