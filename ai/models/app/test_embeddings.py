#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
嵌入向量功能测试脚本
这个脚本用于测试模型生成文本嵌入向量的能力，并进行相似度计算
"""

import requests
import json
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
import time
import logging
import traceback

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

# 嵌入API端点
EMBEDDING_API_URL = "http://localhost:8000/aiforward895036582178848768/embeddings"

def get_embedding(text, retry=2, delay=1):
    """获取文本的嵌入向量
    
    Args:
        text: 输入文本
        retry: 重试次数
        delay: 重试间隔(秒)
    
    Returns:
        嵌入向量或None
    """
    payload = {
        "input": text,
        "model": "unsloth/DeepSeek-R1-Distill-Qwen-14B"
    }
    
    # 打印详细信息
    logging.info(f"发送请求至: {EMBEDDING_API_URL}")
    logging.info(f"请求体: {json.dumps(payload, ensure_ascii=False)[:100]}...")
    
    for attempt in range(retry + 1):
        try:
            response = requests.post(EMBEDDING_API_URL, json=payload, timeout=30)
            
            # 打印服务器响应的状态和内容
            logging.info(f"服务器响应状态码: {response.status_code}")
            
            # 如果发生错误，尝试打印错误消息
            if response.status_code != 200:
                logging.error(f"服务器错误响应: {response.text[:500]}")
                
                # 休息一会后重试
                if attempt < retry:
                    logging.info(f"将在{delay}秒后重试 ({attempt+1}/{retry})")
                    time.sleep(delay)
                    continue
                return None
            
            result = response.json()
            if 'data' in result and len(result['data']) > 0:
                # 打印嵌入向量的统计信息
                embedding = result['data'][0]['embedding']
                logging.info(f"成功获取嵌入向量，维度: {len(embedding)}")
                
                # 打印向量的统计信息
                embedding_array = np.array(embedding)
                logging.info(f"向量统计: 平均值={embedding_array.mean():.4f}, 最大值={embedding_array.max():.4f}, 最小值={embedding_array.min():.4f}")
                
                return embedding
            else:
                logging.error(f"API返回了无效的响应: {result}")
                return None
        except Exception as e:
            logging.error(f"获取嵌入向量时出错: {str(e)}")
            logging.error(traceback.format_exc())
            
            # 休息一会后重试
            if attempt < retry:
                logging.info(f"将在{delay}秒后重试 ({attempt+1}/{retry})")
                time.sleep(delay)
            else:
                return None

def simulate_embedding(text):
    """模拟嵌入向量生成（当服务器不可用时使用）
    
    创建一个简单的哈希函数，将文本转换为伪向量
    """
    logging.warning("使用模拟向量代替实际API调用")
    
    # 使用文本的哈希值创建伪随机向量
    import hashlib
    
    # 获取文本的MD5哈希
    hash_obj = hashlib.md5(text.encode('utf-8'))
    hash_bytes = hash_obj.digest()
    
    # 将哈希字节转换为数字序列
    import struct
    nums = [struct.unpack('B', bytes([b]))[0] for b in hash_bytes]
    
    # 使用哈希值作为随机数种子
    import random
    random.seed(sum(nums))
    
    # 生成1536维的伪向量
    vec = [random.uniform(-1, 1) for _ in range(1536)]
    
    # 标准化向量
    norm = sum(x*x for x in vec) ** 0.5
    vec = [x/norm for x in vec]
    
    return vec

def test_book_embeddings(use_simulation=False):
    """测试书籍描述的嵌入向量生成和相似度计算
    
    Args:
        use_simulation: 是否使用模拟向量代替API调用
    """
    # 准备一些书籍描述样本
    books = [
        {
            "title": "Harry Potter and the Philosopher's Stone",
            "description": "哈利·波特是一个普通的男孩，直到他在11岁生日时发现自己是一名巫师，并被霍格沃茨魔法学校录取。这是一部关于魔法、冒险和友谊的故事。"
        },
        {
            "title": "The Lord of the Rings",
            "description": "这是一个关于魔戒的史诗奇幻小说，讲述了弗罗多·巴金斯和他的伙伴们摧毁至尊魔戒，阻止黑暗魔君索伦征服中土世界的故事。"
        },
        {
            "title": "Pride and Prejudice",
            "description": "《傲慢与偏见》是简·奥斯汀的经典小说，讲述了伊丽莎白·班纳特与达西先生克服偏见与傲慢，最终走到一起的爱情故事。"
        },
        {
            "title": "Introduction to Algorithms",
            "description": "这是一本经典的计算机科学教材，涵盖了各种算法和数据结构，包括排序、搜索、图算法等。"
        },
        {
            "title": "Python Programming: A Modern Approach",
            "description": "这本书介绍了Python编程语言的基础知识和高级特性，适合初学者和有经验的程序员。"
        }
    ]
    
    # 获取嵌入向量
    embeddings = []
    for book in books:
        logging.info(f"获取《{book['title']}》的嵌入向量...")
        text = f"书名: {book['title']}\n描述: {book['description']}"
        
        if use_simulation:
            embedding = simulate_embedding(text)
        else:
            embedding = get_embedding(text)
            
        if embedding:
            embeddings.append({
                "title": book["title"],
                "embedding": embedding
            })
        
        # 避免请求过于频繁
        time.sleep(1)
    
    if len(embeddings) < 2:
        if not use_simulation:
            logging.warning("API调用失败，尝试使用模拟向量...")
            return test_book_embeddings(use_simulation=True)
        else:
            logging.error("没有足够的嵌入向量进行相似度测试")
            return
    
    # 计算相似度矩阵
    embedding_matrix = np.array([book["embedding"] for book in embeddings])
    similarity_matrix = cosine_similarity(embedding_matrix)
    
    # 打印相似度结果
    logging.info("========= 相似度矩阵 =========")
    for i in range(len(embeddings)):
        for j in range(len(embeddings)):
            logging.info(f"{embeddings[i]['title']} vs {embeddings[j]['title']}: {similarity_matrix[i][j]:.4f}")
    
    # 测试查询相似度
    test_queries = [
        "我想找一本关于魔法和冒险的小说",
        "有没有讲述爱情故事的书",
        "推荐一本计算机编程的书"
    ]
    
    logging.info("\n========= 查询相似度测试 =========")
    for query in test_queries:
        logging.info(f"\n查询: '{query}'")
        
        if use_simulation:
            query_embedding = simulate_embedding(query)
        else:
            query_embedding = get_embedding(query)
        
        if query_embedding:
            # 计算查询与每本书的相似度
            similarities = []
            for i, book in enumerate(embeddings):
                sim = cosine_similarity([query_embedding], [book["embedding"]])[0][0]
                similarities.append((book["title"], sim))
            
            # 按相似度排序
            similarities.sort(key=lambda x: x[1], reverse=True)
            
            # 打印结果
            logging.info("查询结果（按相似度排序）:")
            for title, sim in similarities:
                logging.info(f"  - {title}: {sim:.4f}")
        
        # 避免请求过于频繁
        time.sleep(1)

if __name__ == "__main__":
    logging.info("开始测试嵌入向量功能...")
    test_book_embeddings()
    logging.info("测试完成") 