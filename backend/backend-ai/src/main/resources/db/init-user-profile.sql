-- 创建书籍表
CREATE TABLE IF NOT EXISTS `book` (
  `id` VARCHAR(36) NOT NULL COMMENT '书籍ID (UUID)',
  `title` VARCHAR(255) NOT NULL COMMENT '书籍标题',
  `author` VARCHAR(100) DEFAULT NULL COMMENT '作者',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '类别',
  `description` TEXT DEFAULT NULL COMMENT '简介',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍表';

-- 创建用户兴趣表 (用户-书籍关联)
CREATE TABLE IF NOT EXISTS `user_book_interest` (
  `id` VARCHAR(36) NOT NULL COMMENT '记录ID (UUID)',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `book_id` VARCHAR(36) NOT NULL COMMENT '书籍ID',
  `interest_level` INT DEFAULT 1 COMMENT '兴趣级别 (1-5)',
  `mention_count` INT DEFAULT 1 COMMENT '提及次数',
  `first_mention_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次提及时间',
  `last_mention_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近提及时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_book` (`user_id`, `book_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户书籍兴趣表';

-- 创建用户交互的问题记录表
CREATE TABLE IF NOT EXISTS `user_question_topic` (
  `id` VARCHAR(36) NOT NULL COMMENT '记录ID (UUID)',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `topic` VARCHAR(100) NOT NULL COMMENT '问题主题',
  `count` INT DEFAULT 1 COMMENT '提问次数',
  `last_question` TEXT DEFAULT NULL COMMENT '最近一次提问内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_topic` (`user_id`, `topic`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户问题主题表';

-- 添加外键约束
ALTER TABLE `user_book_interest` 
ADD CONSTRAINT `fk_interest_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `user_book_interest` 
ADD CONSTRAINT `fk_interest_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `user_question_topic` 
ADD CONSTRAINT `fk_question_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE; 