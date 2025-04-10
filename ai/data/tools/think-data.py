import json
import re
import requests
import mysql.connector
from mysql.connector import Error
from tqdm import tqdm
from time import sleep
import logging

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('processing.log'),
        logging.StreamHandler()
    ]
)

DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '5233',
    'database': 'book_db',
    'charset': 'utf8mb4'
}

API_ENDPOINT = "http://10.100.1.92:6080/aiforward890022341088641024/generate"

PROMPT_TEMPLATE = """请根据以下咨询对话生成图书馆助理回答时的思考过程：
用户提问：{question}
助理回答：{answer}

要求：
体现处理问题的逻辑推理步骤,输出内容仅包含think内容
"""

def extract_qa(text):
    try:
        user_match = re.search(r"用户问：(.+?)\n\n助理答：", text, re.DOTALL)
        answer_match = re.search(r"助理答：\n(.+)", text, re.DOTALL)
        return (
            user_match.group(1).strip().replace('\n', ' ') if user_match else "",
            answer_match.group(1).strip() if answer_match else ""
        )
    except Exception as e:
        logging.error(f"内容提取失败: {str(e)}")
        return "", ""

def safe_api_call(payload, max_retries=5):
    """带熔断机制的API调用"""
    backoff_factor = 1  # 退避基数
    for attempt in range(max_retries):
        try:
            response = requests.post(
                API_ENDPOINT,
                json=payload,
                timeout=300  # 增加超时到60秒
            )
            
            if response.status_code == 200:
                return response.json()
                
            # 处理特定状态码
            if response.status_code in [429, 502, 503, 504]:
                sleep_time = backoff_factor * (2 ** attempt)
                logging.warning(f"遇到 {response.status_code} 错误，第 {attempt+1} 次重试，等待 {sleep_time}秒")
                sleep(sleep_time)
                continue
                
            response.raise_for_status()
            
        except requests.exceptions.Timeout:
            logging.warning(f"请求超时，第 {attempt+1} 次重试")
            sleep(backoff_factor * (2 ** attempt))
        except requests.exceptions.RequestException as e:
            logging.error(f"请求异常: {str(e)}")
            return None
            
    logging.error("超过最大重试次数")
    return None

def generate_think(question, answer):
    """生成思考内容（严格单线程）"""
    if not question or not answer:
        logging.error("无效的输入问题或回答")
        return None

    payload = {
        "text": PROMPT_TEMPLATE.format(question=question, answer=answer),
        "max_length": 10000
    }

    result = safe_api_call(payload)
    print("返回结果",result)
    if result and 'generated_text' in result:
        return result['generated_text'].strip()
    return None

def process_single_record(record):
    """处理单条记录的全流程"""
    try:
        # 提取问答内容
        question, answer = extract_qa(record['conversation'][0]['output'])
        print(question, answer)
        if not question or not answer:
            logging.warning("跳过无效记录")
            return None

        # 生成思考内容
        think_content = generate_think(question, answer)
        if not think_content:
            logging.warning("思考内容生成失败")
            return None

        # 构建数据库记录
        return {
            'extracted_question': question,
            'extracted_answer': answer,
            'think_content': think_content
        }
    except Exception as e:
        logging.error(f"处理记录异常: {str(e)}")
        return None

def save_single_record(conn, record):
    """保存单条记录到数据库"""
    try:
        cursor = conn.cursor()
        cursor.execute(
            """INSERT INTO library_qa 
            (extracted_question, extracted_answer, think_content) 
            VALUES (%s, %s, %s)""",
            (
                record['extracted_question'],
                record['extracted_answer'],
                record['think_content']
            )
        )
        conn.commit()
        return True
    except Error as e:
        logging.error(f"数据库保存失败: {str(e)}")
        conn.rollback()
        return False
    finally:
        if cursor: cursor.close()

def main(jsonl_files):
    """严格串行处理主流程"""
    conn = mysql.connector.connect(**DB_CONFIG)
    
    for file_path in jsonl_files:
        logging.info(f"开始处理文件: {file_path}")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            for line in tqdm(f, desc=f"处理 {file_path}"):
                try:
                    raw_record = json.loads(line)
                    processed = process_single_record(raw_record)
                    if processed and save_single_record(conn, processed):
                        logging.info(f"成功保存记录: {processed['extracted_question'][:30]}...")
                    else:
                        logging.warning("记录处理失败，已跳过")
                except json.JSONDecodeError:
                    logging.error("JSON解析失败，跳过无效行")
                except Exception as e:
                    logging.error(f"处理异常: {str(e)}")
    
    if conn.is_connected():
        conn.close()

if __name__ == "__main__":
    jsonl_files = ["E:/work/Liberary-Ai/ai\data/raw/deduplicated_data2.jsonl"]
    main(jsonl_files)