-- 创建会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` VARCHAR(36) NOT NULL COMMENT '会话ID (UUID)',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `title` VARCHAR(100) DEFAULT '新对话' COMMENT '会话标题',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_message_preview` VARCHAR(255) DEFAULT NULL COMMENT '最后一条消息预览',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 创建消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` VARCHAR(36) NOT NULL COMMENT '消息ID (UUID)',
  `session_id` VARCHAR(36) NOT NULL COMMENT '会话ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色 (user/assistant)',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- 添加外键约束
ALTER TABLE `chat_message` 
ADD CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- 如果user表已存在，添加外键约束
ALTER TABLE `chat_session` 
ADD CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `chat_message` 
ADD CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE; 