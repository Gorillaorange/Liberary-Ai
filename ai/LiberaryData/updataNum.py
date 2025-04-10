import pymysql
import random
import time
from tqdm import tqdm

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '5233',
    'database': 'book_db',
    'charset': 'utf8mb4'
}

def generate_place():
    """生成书架位置"""
    floor = random.randint(1, 3)
    room = f"{random.randint(1, 20):02d}"
    shelf = random.randint(1, 10)
    layer = random.randint(1, 6)
    return f"{floor}{room}-{shelf}-{layer}"

def generate_num():
    """生成库存数量（0-1000之间的随机数）"""
    return random.randint(0, 20)

def update_records():
    """高效更新函数"""
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            # 获取所有书籍ID
            cursor.execute("SELECT id FROM tushu")
            all_ids = [row[0] for row in cursor.fetchall()]
            
            # 批量处理参数
            batch_size = 50  # 每批处理量
            sleep_time = 0.3  # 批次间隔
            
            # 使用进度条
            with tqdm(total=len(all_ids), desc="更新进度", unit="条") as pbar:
                for i in range(0, len(all_ids), batch_size):
                    batch_ids = all_ids[i:i+batch_size]
                    update_values = []
                    
                    # 生成批量数据
                    for book_id in batch_ids:
                        data = {
                            'num': generate_num(),
                            'place': generate_place(),
                            'id': book_id
                        }
                        update_values.append( (data['num'], data['place'], data['id']) )
                    
                    # 执行批量更新
                    sql = """
                        UPDATE tushu 
                        SET num = %s, place = %s 
                        WHERE id = %s
                    """
                    try:
                        cursor.executemany(sql, update_values)
                        conn.commit()
                        pbar.update(len(batch_ids))
                    except Exception as e:
                        print(f"批次 {i//batch_size} 失败: {str(e)}")
                        conn.rollback()
                    
                    time.sleep(sleep_time)  # 减轻服务器压力

    except Exception as e:
        print(f"更新失败: {str(e)}")
    finally:
        conn.close()

if __name__ == '__main__':
    update_records()