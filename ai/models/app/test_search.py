import requests
import json
from pymilvus import connections, Collection, utility
import logging
from collections import defaultdict

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 配置
API_BASE_URL = "http://localhost:8001"
COLLECTION_NAME = "book_embeddings"
MILVUS_HOST = "127.0.0.1"
MILVUS_PORT = 19530  # Milvus默认端口

def connect_to_milvus():
    """连接到Milvus数据库"""
    try:
        connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
        logger.info("Successfully connected to Milvus")
        return Collection(name=COLLECTION_NAME)
    except Exception as e:
        logger.error(f"Failed to connect to Milvus: {e}")
        raise

def check_collection_stats(collection):
    """检查集合的统计信息"""
    try:
        # 使用 num_entities 获取集合中的实体数量
        num_entities = collection.num_entities
        logger.info(f"Total records in collection: {num_entities}")
        
        # 获取集合的描述信息
        schema = collection.schema
        logger.info(f"Collection schema: {schema}")
        
        return {
            "num_entities": num_entities,
            "schema": schema
        }
    except Exception as e:
        logger.error(f"Failed to get collection stats: {e}")
        raise

def search_similar_books(query_text, limit=5):
    """测试搜索接口"""
    try:
        response = requests.post(
            f"{API_BASE_URL}/aiforward895686727887224832/search",
            json={
                "query": query_text,
                "limit": limit
            }
        )
        response.raise_for_status()
        results = response.json()
        
        logger.info(f"Search results for query '{query_text}':")
        for hit in results.get("hits", []):
            logger.info(f"Score: {hit.get('score')}")
            logger.info(f"Book ID: {hit.get('book_id')}")
            logger.info(f"Text: {hit.get('text')[:200]}...")  # 只显示前200个字符
            logger.info(f"Metadata: {hit.get('metadata')}")
            logger.info("-" * 50)
        
        return results
    except Exception as e:
        logger.error(f"Search failed: {e}")
        raise

def inspect_collection_data(collection, limit=10):
    """检查集合中的数据"""
    try:
        # 加载集合
        collection.load()
        
        # 获取所有数据
        results = collection.query(
            expr="id >= 0",
            output_fields=["id", "book_id", "text", "metadata"],
            limit=limit
        )
        
        # 按book_id分组检查重复
        book_groups = defaultdict(list)
        for item in results:
            book_id = item.get('book_id')
            book_groups[book_id].append(item)
        
        # 检查重复和完整性问题
        logger.info(f"\nFound {len(results)} records in collection")
        logger.info(f"Unique book_ids: {len(book_groups)}")
        
        for book_id, items in book_groups.items():
            if len(items) > 1:
                logger.info(f"\nDuplicate entries for book_id {book_id}:")
                for item in items:
                    logger.info(f"ID: {item.get('id')}")
                    logger.info(f"Text length: {len(item.get('text', ''))}")
                    logger.info(f"Text preview: {item.get('text')[:100]}...")
                    logger.info(f"Metadata: {item.get('metadata')}")
                    logger.info("-" * 30)
        
        # 检查数据完整性
        logger.info("\nChecking data completeness:")
        for item in results:
            text = item.get('text', '')
            metadata = item.get('metadata', {})
            
            # 检查必要字段
            has_title = "书名:" in text
            has_content = len(text) > 100  # 假设内容应该超过100个字符
            
            if not has_title or not has_content:
                logger.info(f"\nIncomplete data found:")
                logger.info(f"Book ID: {item.get('book_id')}")
                logger.info(f"Has title: {has_title}")
                logger.info(f"Has content: {has_content}")
                logger.info(f"Text length: {len(text)}")
                logger.info(f"Text preview: {text[:100]}...")
                logger.info("-" * 30)
        
        return results
    except Exception as e:
        logger.error(f"Failed to inspect collection data: {e}")
        raise

def main():
    try:
        # 连接到Milvus
        collection = connect_to_milvus()
        
        # 检查集合统计信息
        stats = check_collection_stats(collection)
        
        # 检查集合中的数据
        logger.info("Inspecting collection data...")
        inspect_collection_data(collection)
        
        # 测试搜索
        test_queries = [
            "东野圭吾的推理小说",
            "悬疑推理",
            "解谜"
        ]
        
        for query in test_queries:
            logger.info(f"\nTesting search with query: {query}")
            search_similar_books(query)
        
    except Exception as e:
        logger.error(f"Test failed: {e}")
    finally:
        # 断开连接
        try:
            connections.disconnect("default")
            logger.info("Disconnected from Milvus")
        except Exception as e:
            logger.error(f"Failed to disconnect from Milvus: {e}")

if __name__ == "__main__":
    main() 