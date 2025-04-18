import requests
import pymysql
from time import sleep
from random import randint
import re

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '5233',
    'database': 'book_db',
    'charset': 'utf8mb4'
}

# 请求头列表
HEADERS = [
    {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0'},
]

def get_data(page):
    url = f'https://songer.datasn.com/data/api/v1/u_4db7936df78dfe468fc2/gelei_tushu/main/list/{page}/?app=json&manifest=row'
    
    try:
        response = requests.get(
            url,
            headers=HEADERS[randint(0, len(HEADERS)-1)],
            timeout=10
        )
        response.raise_for_status()
        return response.json()
    except Exception as e:
        print(f'Error fetching page {page}: {str(e)}')
        return None

def safe_int(value, default=0):
    """安全转换整数字段"""
    try:
        return int(value)
    except (ValueError, TypeError):
        return default

def safe_float(value, default=0.0):
    """安全转换浮点数字段"""
    try:
        return float(value)
    except (ValueError, TypeError):
        return default

def parse_book(book_data):
    """改进后的解析函数"""
    return {
        'id': safe_int(book_data.get('tushu.id')),
        'title': book_data.get('tushu.title', ''),
        'pingfen': safe_float(book_data.get('tushu.pingfen')),
        'pingjia_renshu': safe_int(book_data.get('tushu.pingjia_renshu')),
        'chuban': book_data.get('tushu.chuban', ''),
        'neirong_jianjie': book_data.get('tushu.neirong_jianjie', ''),
        'zuozhe_jianjie': book_data.get('tushu.zuozhe_jianjie', ''),
        'chubanshe': book_data.get('tushu.chubanshe', ''),
        'yuanzuoming': book_data.get('tushu.yuanzuoming', ''),
        'chubannian': book_data.get('tushu.chubannian', ''),
        'yeshu': safe_int(book_data.get('tushu.yeshu')),
        'dingjia': parse_price(book_data.get('tushu.dingjia')),
        'isbn': book_data.get('tushu.isbn', '')
    }

def parse_price(price_str):
    """增强版价格解析"""
    try:
        # 处理带括号的情况 "132.00（全三册）"
        clean_str = re.sub(r'[^\d.]', '', str(price_str).split('（')[0])
        return float(clean_str)
    except:
        return 0.0
def save_to_db(books, categories):
    """批量存储数据"""
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            # 插入书籍数据
            sql = """INSERT INTO tushu 
                     VALUES (%(id)s, %(title)s, %(pingfen)s, %(pingjia_renshu)s, %(chuban)s,
                     %(neirong_jianjie)s, %(zuozhe_jianjie)s, %(chubanshe)s, %(yuanzuoming)s,
                     %(chubannian)s, %(yeshu)s, %(dingjia)s, %(isbn)s, NOW())
                     ON DUPLICATE KEY UPDATE 
                     title=VALUES(title), pingfen=VALUES(pingfen)"""
            cursor.executemany(sql, books)
            
            # 插入分类数据
            if categories:
                sql = "INSERT INTO category_relation (tushu_id, category_id, category_title) VALUES (%s, %s, %s)"
                cursor.executemany(sql, categories)
            
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"Database error: {str(e)}")
    finally:
        conn.close()

def main():
    page = 1
    max_retry = 3
    
    while True:
        data = None
        for _ in range(max_retry):
            data = get_data(page)
            if data: break
            sleep(5)
        
        if not data or 'output' not in data or 'rows' not in data['output']:
            print(f'Stop at page {page}')
            break

        books = []
        categories = []
        
        # 解析数据
        for book_id, book_data in data['output']['rows'].items():
            # 解析主数据
            book = parse_book(book_data)
            books.append(book)
            
            # 解析分类数据
            category_info = book_data.get('tushu.category_2_x_tushu_id', {})
            for cat in category_info.values():
                categories.append((
                    book['id'],
                    cat.get('category_2.id'),
                    cat.get('category_2.title')
                ))
        
        # 存储数据
        save_to_db(books, categories)
        print(f'Page {page} processed, {len(books)} books saved')
        
        # 翻页控制
        page += 1
        sleep(randint(1, 3))  # 随机延迟防止被封

if __name__ == '__main__':
    main()