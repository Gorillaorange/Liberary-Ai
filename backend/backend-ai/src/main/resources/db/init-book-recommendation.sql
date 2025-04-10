-- 创建图书推荐历史记录表
CREATE TABLE IF NOT EXISTS `book_recommendation_history` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `book_id` INT NOT NULL COMMENT '图书ID',
  `similarity_score` DECIMAL(5,4) DEFAULT NULL COMMENT '相似度得分',
  `recommendation_source` VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '推荐来源：AI-人工智能生成，TAG-标签匹配，HISTORY-历史行为',
  `is_clicked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被点击：0-未点击，1-已点击',
  `recommendation_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  `expiration_time` DATETIME DEFAULT NULL COMMENT '推荐过期时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`),
  KEY `idx_recommendation_time` (`recommendation_time`),
  UNIQUE KEY `uk_user_book` (`user_id`, `book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书推荐历史记录表';

-- 创建图书向量表（如果使用向量数据库如Milvus，此表可选）
CREATE TABLE IF NOT EXISTS `book_embedding` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `book_id` INT NOT NULL COMMENT '图书ID',
  `embedding` TEXT NOT NULL COMMENT '图书嵌入向量JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_id` (`book_id`),
  INDEX `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书嵌入向量表';

-- 添加外键约束（如果需要）
ALTER TABLE `book_recommendation_history` 
ADD CONSTRAINT `fk_recommendation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- 插入测试数据
INSERT INTO `book_recommendation_history` (`user_id`, `book_id`, `similarity_score`, `recommendation_source`, `is_clicked`, `recommendation_time`)
VALUES 
(1, 1001, 0.8765, 'AI', 0, NOW()),
(1, 1002, 0.7654, 'AI', 1, NOW()),
(1, 1003, 0.6543, 'TAG', 0, NOW()),
(1, 1004, 0.5432, 'AI', 0, NOW()),
(1, 1005, 0.4321, 'HISTORY', 1, NOW()); 