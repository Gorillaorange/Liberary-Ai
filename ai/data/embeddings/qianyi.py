import pymysql
import json

def migrate_data():
    # 连接 MySQL
    mysql_conn = pymysql.connect(**DB_CONFIG)
    cursor = mysql_conn.cursor()
    cursor.execute("SELECT book_id, embedding FROM book_vectors")
    rows = cursor.fetchall()

    # 转换数据格式
    data = [{
        "book_id": row['book_id'],
        "embedding": json.loads(row['embedding'])
    } for row in rows]

    # 批量插入 Milvus Lite
    client = MilvusClient("./milvus_data/books.db")
    client.insert("books", data)
    
    # 创建索引（提升查询性能）
    client.create_index(
        collection_name="books",
        index_params={
            "index_type": "IVF_FLAT",
            "metric_type": "L2",
            "params": {"nlist": 256}
        }
    )