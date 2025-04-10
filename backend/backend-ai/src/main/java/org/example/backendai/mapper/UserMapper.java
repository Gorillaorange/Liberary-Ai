package org.example.backendai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backendai.entity.User;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    
    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户实体
     */
    @Select("SELECT * FROM user WHERE username = #{username} AND status = 1")
    User findByUsername(@Param("username") String username);
    
    /**
     * 根据用户ID查询用户
     * 
     * @param id 用户ID
     * @return 用户实体
     */
    @Select("SELECT * FROM user WHERE id = #{id} AND status = 1")
    User findById(@Param("id") Long id);
    
    /**
     * 保存用户
     * 
     * @param user 用户实体
     * @return 影响行数
     */
    @Insert("INSERT INTO user (username, password, role, status, grade, major, create_time, update_time) " +
            "VALUES (#{username}, #{password}, #{role}, #{status}, #{grade}, #{major}, NOW(), NOW())")
    int saveUser(User user);
    
    /**
     * 更新用户基本信息
     * 
     * @param user 用户实体
     * @return 影响行数
     */
    @Update("UPDATE user SET username = #{username}, grade = #{grade}, major = #{major}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updateUserInfo(User user);
    
    /**
     * 更新用户密码
     * 
     * @param id 用户ID
     * @param password 新密码
     * @return 影响行数
     */
    @Update("UPDATE user SET password = #{password}, update_time = NOW() WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);
} 