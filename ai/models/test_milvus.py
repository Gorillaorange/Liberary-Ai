import requests
import json
import argparse
import logging
from typing import List, Dict, Any

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def test_search(query: str, limit: int = 5, base_url: str = "http://localhost:8001") -> List[Dict[str, Any]]:
    """
    测试 Milvus 检索功能
    
    Args:
        query: 查询文本
        limit: 返回结果数量
        base_url: API 基础 URL
    
    Returns:
        检索结果列表
    """
    try:
        # 构建请求 URL
        url = f"{base_url}/aiforward895686727887224832/search"
        
        # 构建请求体
        payload = {
            "query": query,
            "limit": limit
        }
        
        # 发送请求
        logger.info(f"发送请求: {query}")
        response = requests.post(url, json=payload)
        
        # 检查响应状态
        if response.status_code != 200:
            logger.error(f"请求失败: {response.status_code}")
            logger.error(f"错误信息: {response.text}")
            return []
        
        # 解析响应
        result = response.json()
        hits = result.get("hits", [])
        
        # 记录结果
        logger.info(f"找到 {len(hits)} 个结果")
        for i, hit in enumerate(hits, 1):
            logger.info(f"结果 {i}:")
            logger.info(f"  分数: {hit['score']:.4f}")
            logger.info(f"  图书ID: {hit['book_id']}")
            logger.info(f"  文本: {hit['text'][:100]}...")  # 只显示前100个字符
            if hit.get('metadata'):
                logger.info(f"  元数据: {json.dumps(hit['metadata'], ensure_ascii=False)}")
        
        return hits
    except Exception as e:
        logger.error(f"测试过程中发生错误: {e}")
        return []

def main():
    # 创建命令行参数解析器
    parser = argparse.ArgumentParser(description='测试 Milvus 检索功能')
    parser.add_argument('--query', type=str, required=True, help='查询文本')
    parser.add_argument('--limit', type=int, default=5, help='返回结果数量')
    parser.add_argument('--url', type=str, default="http://localhost:8001", help='API 基础 URL')
    
    # 解析参数
    args = parser.parse_args()
    
    # 执行测试
    test_search(args.query, args.limit, args.url)

if __name__ == "__main__":
    main() 