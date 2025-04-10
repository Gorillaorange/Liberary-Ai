from pymilvus import connections, Collection, utility
import numpy as np
import logging
import argparse

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def test_milvus(collection_name: str = "book_embeddings", host: str = "localhost", port: int = 19530):
    """
    直接测试 Milvus 数据库
    
    Args:
        collection_name: 集合名称
        host: Milvus 服务器地址
        port: Milvus 服务器端口
    """
    try:
        # 连接 Milvus
        logger.info(f"正在连接 Milvus: {host}:{port}")
        connections.connect(host=host, port=port)
        
        # 检查集合是否存在
        if not utility.has_collection(collection_name):
            logger.error(f"集合 {collection_name} 不存在")
            return
        
        # 获取集合
        collection = Collection(collection_name)
        collection.load()
        
        # 检查集合状态
        num_entities = collection.num_entities
        logger.info(f"集合 {collection_name} 包含 {num_entities} 个实体")
        
        # 检查索引
        if not collection.has_index():
            logger.warning("集合没有索引")
        else:
            index_info = collection.index()
            logger.info(f"索引信息: {index_info}")
        
        # 生成测试向量
        test_vector = np.random.rand(1536).tolist()  # 假设向量维度为1536
        logger.info(f"生成的测试向量维度: {len(test_vector)}")
        
        # 执行搜索测试
        search_params = {
            "metric_type": "L2",
            "params": {
                "nprobe": 16,
                "radius": 1000.0
            }
        }
        
        logger.info("执行搜索测试...")
        results = collection.search(
            data=[test_vector],
            anns_field="embedding",
            param=search_params,
            limit=5,
            output_fields=["book_id", "text"]
        )
        
        # 输出结果
        logger.info(f"找到 {len(results[0])} 个结果")
        for i, hit in enumerate(results[0], 1):
            logger.info(f"结果 {i}:")
            logger.info(f"  距离: {hit.distance}")
            logger.info(f"  图书ID: {hit.entity.get('book_id')}")
            logger.info(f"  文本: {hit.entity.get('text')[:100]}...")
        
        # 断开连接
        connections.disconnect("default")
        logger.info("测试完成")
        
    except Exception as e:
        logger.error(f"测试过程中发生错误: {e}")
        raise

def main():
    # 创建命令行参数解析器
    parser = argparse.ArgumentParser(description='直接测试 Milvus 数据库')
    parser.add_argument('--collection', type=str, default="book_embeddings", help='集合名称')
    parser.add_argument('--host', type=str, default="localhost", help='Milvus 服务器地址')
    parser.add_argument('--port', type=int, default=19530, help='Milvus 服务器端口')
    
    # 解析参数
    args = parser.parse_args()
    
    # 执行测试
    test_milvus(args.collection, args.host, args.port)

if __name__ == "__main__":
    main() 