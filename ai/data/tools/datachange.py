import json
import mysql.connector
from mysql.connector import Error
from tqdm import tqdm

DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '5233',
    'database': 'book_db',
    'charset': 'utf8mb4'
}

def format_output(think, answer):
    """构建标准输出格式"""
    return f"<think>{think.strip()}\n</think>\n<answer>\n{answer.strip()}</answer>"

def fetch_data(batch_size=500):
    """分批获取原始数据"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor(dictionary=True)
        
        # 获取有效数据总数
        cursor.execute("""
            SELECT COUNT(*) 
            FROM library_qa 
            WHERE think_content IS NOT NULL 
            AND CHAR_LENGTH(think_content) > 20
        """)
        total = cursor.fetchone()['COUNT(*)']
        
        # 分页获取数据
        offset = 0
        with tqdm(total=total, desc="数据读取") as pbar:
            while True:
                cursor.execute("""
                    SELECT 
                        extracted_question AS input,
                        extracted_answer AS answer,
                        think_content AS think
                    FROM library_qa
                    WHERE think_content IS NOT NULL
                    LIMIT %s OFFSET %s
                """, (batch_size, offset))
                
                batch = cursor.fetchall()
                if not batch:
                    break
                
                yield batch
                offset += len(batch)
                pbar.update(len(batch))
                
    except Error as e:
        print(f"数据库错误: {str(e)}")
    finally:
        if conn.is_connected():
            cursor.close()
            conn.close()

def convert_and_save(output_file):
    """转换并保存为JSONL格式"""
    with open(output_file, 'w', encoding='utf-8') as f:
        for batch in fetch_data():
            for record in batch:
                # 过滤无效数据
                if not all([record['answer'], record['think']]):
                    continue
                
                # 构建标准格式
                formatted = {
                    "instruction": "你是一个专业的图书馆咨询员",
                    "input": record['input'],
                    "output": format_output(
                        record['think'],
                        record['answer']
                    )
                }
                
                # 写入文件
                f.write(json.dumps(formatted, ensure_ascii=False) + "\n")

if __name__ == "__main__":
    convert_and_save("fine_tuning_dataset.jsonl")
    print("转换完成！生成文件：fine_tuning_dataset.jsonl")