import json
import re
from json import JSONDecodeError

def clean_control_characters(text):
    # 移除 ASCII 0-31 范围内的非法控制字符（保留 \t, \n, \r）
    return re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', '', text)

with open("library_data3.jsonl", "r") as f:
    for line in f:
        cleaned_line = clean_control_characters(line)
        try:
            data = json.loads(cleaned_line)
        except json.JSONDecodeError as e:
            print(f"解析失败：{e.msg}，位置 {e.pos}")


def parse_nested_json(content):
    try:
        # 替换未转义的双引号和斜杠
        sanitized = content.replace('\\"', '"').replace('\\\\', '\\')
        return json.loads(sanitized)
    except JSONDecodeError as e:
        print(f"嵌套解析失败：{e}")
        return None
    


def safe_json_loads(data):
    try:
        return json.loads(data)
    except json.JSONDecodeError as e:
        # 输出错误位置前后 50 个字符
        start = max(0, e.pos - 50)
        end = min(len(data), e.pos + 50)
        context = data[start:end]
        print(f"错误位置 {e.pos} 附近内容：{context}")
        raise