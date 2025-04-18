import json
import re
import mysql.connector
from mysql.connector import Error
from openai import OpenAI
import time
from tqdm import tqdm

# 腾讯API配置
TENCENT_CONFIG = {
    "api_key": "sk-PQj8lgRQzljlnhhzgj5Mf3kKQs8bM0l2bjgnF8sBGpALtN7F",
    "base_url": "https://api.lkeap.cloud.tencent.com/v1"
}

# MySQL配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '5233',
    'database': 'book_db',
    'charset': 'utf8mb4'
}

PROMPT_TEMPLATE = """请根据以下咨询对话生成助理回答时的思考过程
用户提问：{question}
助理回答：{answer}

要求：
体现处理问题的逻辑推理步骤,输出内容仅为think内容，去除**号"""

class StreamProcessor:
    def __init__(self):
        self.client = OpenAI(**TENCENT_CONFIG)
        self.db_conn = mysql.connector.connect(**DB_CONFIG)
        
        # 初始化数据库表
        self._init_db()

    def _init_db(self):
        """创建数据库表（如果不存在）"""
        cursor = self.db_conn.cursor()
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS library_qa (
                id INT AUTO_INCREMENT PRIMARY KEY,
                original_input TEXT,
                original_output TEXT,
                extracted_question TEXT,
                extracted_answer TEXT,
                think_content TEXT,
                process_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) CHARSET=utf8mb4
        """)
        self.db_conn.commit()
        cursor.close()

    def _save_record(self, record):
        """实时保存单条记录到数据库"""
        cursor = self.db_conn.cursor()
        try:
            cursor.execute(
                """INSERT INTO library_qa 
                (original_input, original_output, extracted_question, extracted_answer, think_content) 
                VALUES (%s, %s, %s, %s, %s)""",
                (
                    record['original_input'],
                    record['original_output'],
                    record['extracted_question'],
                    record['extracted_answer'],
                    record['think_content']
                )
            )
            self.db_conn.commit()
            return cursor.rowcount
        except Error as e:
            print(f"数据库保存失败: {str(e)}")
            self.db_conn.rollback()
            return 0
        finally:
            cursor.close()

    def process_stream(self, prompt):
        """处理流式响应并实时捕获 think_content"""
        think_content = ""
        
        # 创建流式请求
        stream = self.client.chat.completions.create(
            model="deepseek-v3",
            messages=[{"role": "user", "content": prompt}],
            stream=True,
            max_tokens=2000,
            temperature=0.7
        )

        # 处理流数据
        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta:
                delta = chunk.choices[0].delta
                
                # 捕获 think_content 字段
                if hasattr(delta, 'think_content') and delta.think_content:
                    print(delta.think_content, end='', flush=True)
                    think_content += delta.think_content
                
                # 如果 think_content 在常规 content 字段
                elif hasattr(delta, 'content') and delta.content:
                    print(delta.content, end='', flush=True)
                    think_content += delta.content

        return think_content.strip()

    def generate_think(self, question, answer):
        """生成思考内容（带重试机制）"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                return self.process_stream(
                    PROMPT_TEMPLATE.format(question=question, answer=answer)
                )
            except Exception as e:
                print(f"API调用失败（尝试{attempt+1}）: {str(e)}")
                time.sleep(2 ** attempt)
        return None

def extract_qa(text):
    """从output字段提取问答内容"""
    try:
        user_match = re.search(r"用户问：(.+?)\n\n助理答：", text, re.DOTALL)
        answer_match = re.search(r"助理答：\n(.+)", text, re.DOTALL)
        return (
            user_match.group(1).strip().replace('\n', ' ') if user_match else "",
            answer_match.group(1).strip() if answer_match else ""
        )
    except Exception as e:
        print(f"内容提取失败: {str(e)}")
        return "", ""

def main(jsonl_files):
    processor = StreamProcessor()
    
    for file_path in jsonl_files:
        print(f"处理文件: {file_path}")
        with open(file_path, 'r', encoding='utf-8') as f:
            for line in tqdm(f, desc="处理进度"):
                try:
                    raw_data = json.loads(line)
                    conv = raw_data.get("conversation", [{}])[0]
                    
                    # 提取原始数据
                    record = {
                        "original_input": conv.get("input", ""),
                        "original_output": conv.get("output", ""),
                        "extracted_question": "",
                        "extracted_answer": "",
                        "think_content": ""
                    }
                    print(f"原始数据: {record}")
                    # 提取问答内容
                    question, answer = extract_qa(record["original_output"])
                    if not question or not answer:
                        continue
                    
                    record.update({
                        "extracted_question": question,
                        "extracted_answer": answer
                    })
                    
                    print(f"问答内容: {question} / {answer}")
                    # 生成思考内容
                    
                    think = processor.generate_think(question, answer)
                    print(f"思考内容: {think}")
                    if think and think.strip():
                        record["think_content"] = think
                        processor._save_record(record)
                        
                except Exception as e:
                    print(f"处理异常: {str(e)}")
                    continue

if __name__ == "__main__":
    jsonl_files = ["E:/work/Liberary-Ai/ai\data/raw/deduplicated_data2.jsonl"]

    main(jsonl_files)