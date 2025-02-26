import json
import re
import sys
import hashlib
from pathlib import Path
from collections import defaultdict
from json.decoder import WHITESPACE

class SafeJSONDecoder(json.JSONDecoder):
    """增强型JSON解析器，处理非法控制字符"""
    def decode(self, s, _w=WHITESPACE.match):
        s = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', '', s)
        return super().decode(s, _w)

UNICODE_SYMBOL_PATTERN = re.compile(
    r'[\u1F600-\u1F64F\u1F300-\u1F5FF\u1F680-\u1F6FF'
    r'\u1F700-\u1F77F\u1F780-\u1F7FF\u1F800-\u1F8FF'
    r'\u1F900-\u1F9FF\u02700-\u027BF\u024C2-\u1F251'
    r'\u02500-\u02BEF\u03000-\u0303F]+',
    flags=re.UNICODE
)

def remove_unicode_symbols(text):
    """清除Unicode装饰符号并过滤控制字符"""
    cleaned = UNICODE_SYMBOL_PATTERN.sub('', text)
    return re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', '', cleaned).strip()

def clean_local_jsonl(input_file, output_file, max_samples=5):
    input_path = Path(input_file).resolve()
    if not input_path.exists():
        raise FileNotFoundError(f"输入文件不存在: {input_path}")

    intent_counter = defaultdict(int)
    seen_hashes = set()
    repair_log = defaultdict(int)

    with open(input_path, 'r', encoding='utf-8', errors='replace') as fin, \
         open(output_file, 'w', encoding='utf-8') as fout:

        print(f"[Start] 开始处理文件: {input_path.name}")

        for line_num, line in enumerate(fin, 1):
            try:
                # 预处理和解析外层JSON
                clean_line = line.encode('utf-8', 'replace').decode('utf-8')
                outer = json.loads(clean_line, cls=SafeJSONDecoder)
                
                # 提取助理消息
                assistant_msg = next(m for m in outer['messages'] if m['role'] == 'assistant')
                
                # 解析嵌套内容
                inner_content = json.loads(assistant_msg['content'], cls=SafeJSONDecoder)
                real_qa = inner_content['messages']
                
                # 清洗内容
                raw_question = real_qa[0]['content']
                raw_answer = real_qa[1]['content']
                clean_question = remove_unicode_symbols(raw_question)
                clean_answer = remove_unicode_symbols(raw_answer)

                # 记录修复情况
                if len(raw_question) != len(clean_question):
                    repair_log['question'] += 1
                if len(raw_answer) != len(clean_answer):
                    repair_log['answer'] += 1

                # 哈希去重
                content_hash = hashlib.md5(
                    f"{clean_question}||{clean_answer}".encode('utf-8')
                ).hexdigest()
                
                # 意图分类
                is_rule = any(kw in clean_answer for kw in ["积分规则", "加分项", "减分项"])
                intent = "规则咨询" if is_rule else "书籍推荐"
                
                if content_hash not in seen_hashes:
                    if intent_counter[intent] < max_samples:
                        clean_data = {
                            "messages": [
                                {"role": "user", "content": clean_question},
                                {"role": "assistant", "content": clean_answer}
                            ],
                            "metadata": {
                                "intent": intent,
                                "source_hash": content_hash,
                                "original_length": len(raw_answer),
                                "repaired": len(raw_answer) != len(clean_answer)
                            }
                        }
                        fout.write(json.dumps(clean_data, ensure_ascii=False) + '\n')
                        intent_counter[intent] += 1
                    
                    seen_hashes.add(content_hash)

                # 进度显示
                if line_num % 10 == 0:
                    sys.stdout.write(f"\r[Progress] 已处理: {line_num} 行")
                    sys.stdout.flush()

            except Exception as e:
                print(f"\n[Warn] 行 {line_num} 异常: {str(e)}")
                continue

    # 输出统计报告
    print(f"\n[Complete] 处理完成！")
    print(f"[Stats] 规则咨询: {intent_counter['规则咨询']} 条")
    print(f"[Stats] 书籍推荐: {intent_counter['书籍推荐']} 条")
    print(f"[Repair] 问题修复: {repair_log.get('question',0)} 次")
    print(f"[Repair] 回答修复: {repair_log.get('answer',0)} 次")
    print(f"[Output] 保存路径: {Path(output_file).resolve()}")

if __name__ == "__main__":
    clean_local_jsonl(
        input_file="ai/data/raw/library_data3.jsonl",
        output_file="ai/data/processed/lb3.jsonl",
        max_samples=0
    )