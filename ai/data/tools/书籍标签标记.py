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

import pymysql
import json
import requests
import time
import re
from typing import List, Dict, Any, Tuple

# 定义图书分类类型
BOOK_CATEGORIES = [
    "小说", "科技", "历史", "科学", "哲学", "艺术", "商业", "心理学", "教育", "生活",
    "文学", "计算机", "经济", "政治", "自然科学", "医学", "工程", "社会学", "语言", "宗教"
]

# 连接数据库
def connect_db():
    try:
        conn = pymysql.connect(
            host=DB_CONFIG['host'],
            user=DB_CONFIG['user'],
            password=DB_CONFIG['password'],
            database=DB_CONFIG['database'],
            charset=DB_CONFIG['charset'],
            cursorclass=pymysql.cursors.DictCursor
        )
        print("数据库连接成功")
        return conn
    except Exception as e:
        print(f"数据库连接错误: {e}")
        return None

# 从数据库获取未标记的图书
def get_untagged_books(conn, limit=504):
    try:
        with conn.cursor() as cursor:
            sql = """
            SELECT id, title, zuozhe_jianjie, chuban 
            FROM tushu 
            WHERE tags IS NULL OR tags = ''
            LIMIT %s
            """
            cursor.execute(sql, (limit,))
            books = cursor.fetchall()
            print(f"获取到 {len(books)} 本未标记的图书")
            return books
    except Exception as e:
        print(f"获取未标记图书错误: {e}")
        return []

# 向AI发送请求
def query_ai(prompt):
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {TENCENT_CONFIG['api_key']}"
    }
    
    data = {
        "model": "deepseek-v3",
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.5,
        "max_tokens": 2000
    }
    
    try:
        response = requests.post(
            f"{TENCENT_CONFIG['base_url']}/chat/completions", 
            headers=headers,
            json=data,
            timeout=30
        )
        
        if response.status_code == 200:
            result = response.json()
            return result["choices"][0]["message"]["content"]
        else:
            print(f"API请求失败: {response.status_code}, {response.text}")
            return None
    except Exception as e:
        print(f"API请求异常: {e}")
        return None

# 解析AI响应，提取标签和分类
def parse_ai_response(response: str) -> Tuple[List[str], str]:
    # 尝试提取JSON格式的响应
    json_pattern = r'\{[\s\S]*\}'
    json_match = re.search(json_pattern, response)
    
    if json_match:
        try:
            result = json.loads(json_match.group())
            categories = result.get('categories', [])
            tags = result.get('tags', [])
            main_category = result.get('main_category', '')
            
            # 验证分类是否在预定义类别中
            if main_category and main_category not in BOOK_CATEGORIES:
                main_category = ''
            
            # 限制标签数量和长度
            valid_tags = []
            for tag in tags:
                if isinstance(tag, str) and 1 <= len(tag) <= 10:
                    valid_tags.append(tag)
                if len(valid_tags) >= 5:  # 最多5个标签
                    break
            
            return valid_tags, main_category
        except json.JSONDecodeError:
            pass
    
    # 如果JSON解析失败，尝试正则提取
    # 提取标签
    tags_pattern = r'标签[：:]\s*(.*?)(?=\n|$)'
    tags_match = re.search(tags_pattern, response)
    tags = []
    
    if tags_match:
        tags_text = tags_match.group(1)
        # 分割标签，可能由逗号、空格或其他分隔符分隔
        for tag in re.split(r'[,，、\s]+', tags_text):
            if tag and 1 <= len(tag) <= 10:
                tags.append(tag)
                if len(tags) >= 5:  # 最多5个标签
                    break
    
    # 提取分类
    category_pattern = r'分类[：:]\s*(.*?)(?=\n|$)'
    category_match = re.search(category_pattern, response)
    main_category = ''
    
    if category_match:
        potential_category = category_match.group(1).strip()
        # 验证分类是否在预定义类别中
        for category in BOOK_CATEGORIES:
            if category in potential_category:
                main_category = category
                break
    
    return tags, main_category

# 更新图书标签和分类到数据库
def update_book_tags(conn, book_id, tags, category):
    try:
        with conn.cursor() as cursor:
            sql = """
            UPDATE tushu 
            SET tags = %s, category = %s 
            WHERE id = %s
            """
            tags_json = json.dumps(tags, ensure_ascii=False)
            cursor.execute(sql, (tags_json, category, book_id))
        conn.commit()
        print(f"成功更新图书 ID {book_id} 的标签和分类")
        return True
    except Exception as e:
        print(f"更新图书标签错误: {e}")
        conn.rollback()
        return False

# 主处理函数
def process_books():
    conn = connect_db()
    if not conn:
        return
    
    try:
        # 获取未标记的图书
        books = get_untagged_books(conn)
        if not books:
            print("没有未标记的图书需要处理")
            return
        
        # 处理每本图书
        for book in books:
            book_id = book.get('id')
            book_title = book.get('title', '未知书名')
            book_author = book.get('zuozhe_jianjie', '未知作者')
            book_publisher = book.get('chuban', '未知出版社')
            
            print(f"\n正在处理图书: {book_title} (ID: {book_id})")
            
            # 构建AI查询提示
            prompt = f"""
你是一个专业的图书分类专家，请根据以下图书信息，提供合适的标签和分类：

图书标题: {book_title}
作者简介: {book_author}
出版社: {book_publisher}

请从以下分类中选择一个最合适的主要分类：
{', '.join(BOOK_CATEGORIES)}

同时，提供3-5个描述这本书特点的标签词，每个标签不超过10个字符。

请以JSON格式返回结果，格式如下:
{{
    "main_category": "所属分类",
    "tags": ["标签1", "标签2", "标签3", "标签4", "标签5"],
    "reasoning": "简要的分类和标签理由"
}}
            """
            
            # 查询AI获取响应
            print("正在向AI发送查询...")
            response = query_ai(prompt)
            
            if not response:
                print("AI响应为空，跳过此图书")
                continue
            
            print(f"AI响应:\n{response[:200]}...")
            
            # 解析AI响应，提取标签和分类
            tags, category = parse_ai_response(response)
            
            if not tags and not category:
                print("无法从AI响应中提取有效标签和分类，跳过此图书")
                continue
            
            print(f"提取的标签: {tags}")
            print(f"提取的分类: {category}")
            
            # 更新图书标签和分类到数据库
            if update_book_tags(conn, book_id, tags, category):
                print(f"成功标记图书 '{book_title}'")
            else:
                print(f"标记图书 '{book_title}' 失败")
            
            # 间隔一下，避免API请求过于频繁
            time.sleep(1)
        
        print("\n完成所有图书的处理")
    
    except Exception as e:
        print(f"处理图书过程中出错: {e}")
    
    finally:
        conn.close()
        print("数据库连接已关闭")

# 创建图书标签表（如果不存在）
def create_tables_if_not_exists():
    conn = connect_db()
    if not conn:
        return
    
    try:
        with conn.cursor() as cursor:
            # 确保tushu表有tags和category字段
            sql = """
            ALTER TABLE books 
            ADD COLUMN IF NOT EXISTS tags TEXT,
            ADD COLUMN IF NOT EXISTS category VARCHAR(50)
            """
            cursor.execute(sql)
            conn.commit()
            print("数据库表结构已更新")
    except Exception as e:
        print(f"更新数据库表结构错误: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    print("开始图书标签标记过程...")
    create_tables_if_not_exists()
    process_books()
    print("图书标签标记过程完成!")