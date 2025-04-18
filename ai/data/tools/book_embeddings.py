#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import json
import logging
import sys
import time
from typing import List, Dict, Any, Set
from tqdm import tqdm
import requests
import pymysql
from pymysql.cursors import DictCursor

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("book_embedding_pipeline")

class BookVectorPipeline:
    def __init__(self, config: Dict[str, Any]):
        """
        初始化管道
        :param config: 包含以下键的配置字典
            mysql: MySQL连接配置
            api: 模型API配置
        """
        self.config = config
        self.mysql_conn = None
        self.processed_book_ids: Set[int] = set()
        self.load_processed_ids()  # 加载已处理的ID
        self.connect_mysql()

    def load_processed_ids(self):
        """从文件加载已处理的ID"""
        try:
            with open('processed_ids.json', 'r') as f:
                self.processed_book_ids = set(json.load(f))
            logger.info(f"已加载 {len(self.processed_book_ids)} 个已处理ID")
        except FileNotFoundError:
            logger.info("未找到已处理ID文件，将从头开始处理")

    def save_processed_ids(self):
        """保存已处理的ID到文件"""
        try:
            with open('processed_ids.json', 'w') as f:
                json.dump(list(self.processed_book_ids), f)
            logger.info(f"已保存 {len(self.processed_book_ids)} 个已处理ID")
        except Exception as e:
            logger.error(f"保存已处理ID失败: {e}")

    def connect_mysql(self):
        """连接MySQL数据库"""
        try:
            mysql_config = self.config['mysql'].copy()
            mysql_config['cursorclass'] = DictCursor
            self.mysql_conn = pymysql.connect(**mysql_config)
            logger.info("MySQL连接成功")
        except Exception as e:
            logger.error(f"MySQL连接失败: {e}")
            sys.exit(1)

    def generate_book_payload(self, book: Dict) -> Dict:
        """构建API请求的有效载荷"""
        return {
            "text": self._build_book_text(book),
            "metadata": {
                "book_id": book["id"],
                "category": book.get("category"),
                "publisher": book.get("chubanshe"),
                "tags": json.loads(book["tags"]) if book.get("tags") else []
            }
        }

    def _build_book_text(self, book: Dict) -> str:
        """构建用于生成向量的文本内容"""
        components = []
        fields = [
            ('title', "书名: {}"),
            ('yuanzuoming', "原作名: {}"),
            ('neirong_jianjie', "内容简介: {}"),
            ('zuozhe_jianjie', "作者信息: {}"),
            ('category', "分类: {}")
        ]
        for field, template in fields:
            if book.get(field):
                components.append(template.format(book[field]))
        return "\n".join(components)

    def get_unprocessed_books(self, batch_size: int) -> List[Dict[str, Any]]:
        """获取未处理的书籍"""
        if not self.mysql_conn or not self.mysql_conn.open:
            self.connect_mysql()
            
        try:
            with self.mysql_conn.cursor() as cursor:
                cursor.execute("""
                    SELECT * FROM tushu 
                    WHERE id NOT IN (%s)
                    LIMIT %s
                """, (','.join(map(str, self.processed_book_ids)), batch_size))
                results = cursor.fetchall()
                return [dict(row) for row in results]
        except Exception as e:
            logger.error(f"查询失败: {e}")
            self.connect_mysql()  # 重新连接
            return []

    def mark_as_processed(self, book_id: int):
        """标记已处理书籍"""
        self.processed_book_ids.add(book_id)
        logger.debug(f"已标记书籍ID: {book_id} 为已处理")

    def process_batch(self, batch: List[Dict]):
        """处理批量数据"""
        success_count = 0
        max_retries = 3
        retry_delay = 5
        
        for book in batch:
            for attempt in range(max_retries):
                try:
                    payload = self.generate_book_payload(book)
                    response = requests.post(
                        self.config['api']['url'],
                        headers=self.config['api']['headers'],
                        json=payload,
                        timeout=30
                    )
                    
                    # 接受200和201作为成功状态码
                    if response.status_code in [200, 201]:
                        self.mark_as_processed(book['id'])
                        success_count += 1
                        logger.debug(f"成功处理书籍ID: {book['id']}")
                        break
                    else:
                        error_msg = response.json().get('error') if response.text else f"HTTP {response.status_code}"
                        logger.error(f"处理失败 (尝试 {attempt + 1}/{max_retries}): {error_msg}")
                        if attempt < max_retries - 1:
                            time.sleep(retry_delay)
                            
                except Exception as e:
                    logger.error(f"处理异常 (尝试 {attempt + 1}/{max_retries}): {str(e)}")
                    if attempt < max_retries - 1:
                        time.sleep(retry_delay)

        return success_count

    def run(self, total_books: int, batch_size: int = 50):
        """运行处理管道"""
        processed = 0
        try:
            with tqdm(total=total_books, desc="处理进度") as pbar:
                while processed < total_books:
                    books = self.get_unprocessed_books(batch_size)
                    if not books:
                        break

                    success = self.process_batch(books)
                    processed += len(books)
                    pbar.update(len(books))
                    logger.info(f"批次完成: 成功{success}/{len(books)}")

                    batch_size = max(10, min(batch_size, int(batch_size * (success/len(books)))))
                    time.sleep(1)
        except KeyboardInterrupt:
            logger.info("收到中断信号，正在保存进度...")
        finally:
            self.save_processed_ids()
            if self.mysql_conn:
                self.mysql_conn.close()

if __name__ == "__main__":
    # 配置示例
    config = {
        "mysql": {
            "host": "localhost",
            "user": "root",
            "password": "5233",
            "database": "book_db",
            "charset": "utf8mb4"
        },
        "api": {
            "url": "http://10.100.1.92:6080/aiforward895686727887224832/embeddings",
            "headers": {
                "Content-Type": "application/json"
            }
        }
    }

    # 获取总书籍数
    try:
        with pymysql.connect(**config['mysql'], cursorclass=DictCursor) as conn:
            with conn.cursor() as cursor:
                cursor.execute("SELECT COUNT(*) AS total FROM tushu")
                result = cursor.fetchone()
                if result and isinstance(result, dict):
                    total = result['total']
                else:
                    logger.error("无法获取总书籍数")
                    sys.exit(1)
    except Exception as e:
        logger.error(f"获取总数失败: {e}")
        sys.exit(1)

    # 运行管道
    pipeline = BookVectorPipeline(config)
    try:
        pipeline.run(total, batch_size=50)
    finally:
        if pipeline.mysql_conn:
            pipeline.mysql_conn.close()