-- 删除已存在的用户表
DROP TABLE IF EXISTS `user`;

-- 创建用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '角色',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级',
  `major` varchar(50) DEFAULT NULL COMMENT '专业',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入测试用户（密码为123456的MD5+BCrypt加密形式）
INSERT INTO `user` (`username`, `password`, `role`, `status`, `grade`, `major`, `create_time`, `update_time`) 
VALUES 
('admin', '$2a$10$Sg72lnSeu5xIjSOObUDIpufa5eN1nwFUTdycj.mhlEzp8tIk4DiCK', 'ADMIN', 1, '2021', '计算机科学与技术', NOW(), NOW()),
('user', '$2a$10$Sg72lnSeu5xIjSOObUDIpufa5eN1nwFUTdycj.mhlEzp8tIk4DiCK', 'USER', 1, '2022', '网络工程', NOW(), NOW()); 