"""
library_data_sync_generator.py
图书馆服务对话数据生成工具（同步版本）
"""

import os
import json
import re
import random
from typing import Dict, List, Optional
from openai import OpenAI
from tenacity import retry, stop_after_attempt, wait_exponential
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

class LibraryDataGenerator:
    def __init__(self):
        self.client = OpenAI(
            api_key=os.getenv("API_KEY"),
            base_url=os.getenv("API_BASE", "https://maas-api.cn-huabei-1.xf-yun.com/v1")
        )
        self.timeout = 15
        self.max_retries = 3

    def _build_system_prompt(self) -> str:
        """构建系统级提示词"""
        return """你是一名专业的图书馆服务助手，需要严格遵循以下规则：
1. 回复必须包含至少两个内容区块标签：[书籍推荐]、[规则说明]、[风险提示]
2. 书籍推荐要求：
   - 每次推荐3-5本不同领域的书籍,
   - 每本书包含学科分类说明（示例：计算机科学/经济学）
3. 条款嵌入规范：
   • 积分规则：+20分/书评、-5分/天逾期
   • 外借规则：30册上限、60天借期
4. 对话结尾必须包含<answer>标记"""

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    def call_api(self, prompt: str) -> str:
        """带重试机制的同步API调用"""
        try:
            response = self.client.chat.completions.create(
                model=os.getenv("SERVICE_ID"),
                messages=[
                    {"role": "system", "content": self._build_system_prompt()},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=512,
                timeout=self.timeout
            )
            return response.choices[0].message.content
        except Exception as e:
            print(f"API调用失败: {str(e)}")
            raise

    def generate_dialogue(self) -> Optional[Dict]:
        """生成完整的多轮对话数据"""
        try:
            # 生成首轮对话
            user_query = self._generate_user_question()
            assistant_response = self._get_valid_response(user_query)
            
            if not assistant_response:
                return None

            dialogue = [{
                "system": self._build_system_prompt(),
                "input": user_query,
                "output": assistant_response
            }]

            # 生成后续追问对话（1-2轮）
            for _ in range(random.randint(1, 2)):
                follow_up = self._generate_followup(assistant_response)
                follow_response = self._get_valid_response(follow_up)
                
                if follow_response:
                    dialogue.append({
                        "input": follow_up,
                        "output": follow_response
                    })

            return self._format_output(dialogue)

        except Exception as e:
            print(f"生成对话时出错: {str(e)}")
            return None

    def _get_valid_response(self, prompt: str) -> Optional[str]:
        """获取并验证API响应"""
        for attempt in range(self.max_retries):
            try:
                response = self.call_api(prompt)
                if self._validate_response(response):
                    return response
                print(f"响应验证失败，第{attempt+1}次重试...")
            except Exception as e:
                print(f"请求失败: {str(e)}")
        return None

    def _validate_response(self, response: str) -> bool:
        """响应内容验证"""
        checks = [
            len(re.findall(r"$$书籍推荐$$", response)) >= 1,
            len(re.findall(r"$$规则说明$$", response)) >= 1,
            re.search(r"\d+册|\d+天|\+?-?\d+分", response),
            3 <= response.count("《") <=5
        ]
        return all(checks)

    def _generate_user_question(self) -> str:
        """生成多样化用户提问"""
        templates = [
            "我想找关于{theme}的{type}",
            "有没有{adjective}的{field}书籍推荐？",
            "请推荐几本{audience}看的{subject}书"
        ]
        params = {
            "theme": random.choice(["量子物理", "人工智能伦理", "现代建筑"]),
            "type": random.choice(["入门书", "科普读物", "学术专著"]),
            "adjective": random.choice(["浅显易懂", "权威全面", "最新出版"]),
            "field": random.choice(["生物科技", "宏观经济", "机器学习"]),
            "audience": random.choice(["大学生", "研究人员", "普通读者"]),
            "subject": random.choice(["经济学", "物理学", "计算机科学"])
        }
        return random.choice(templates).format(**params)

    def _generate_followup(self, previous_response: str) -> str:
        """生成后续追问问题"""
        if "规则说明" in previous_response:
            return random.choice([
                "外文书可以外借吗？",
                "如果逾期归还会怎样？",
                "如何获得更多积分？"
            ])
        return random.choice([
            "这些书的最新版本是什么时候出版的？",
            "有没有相关的电子资源？",
            "能推荐同作者的其他作品吗？"
        ])

    def _format_output(self, dialogue: List) -> Dict:
        """格式化输出结构"""
        return {
            "conversation": dialogue,
            "metadata": {
                "validation": {
                    "passed": True,
                    "missing_sections": self._find_missing_sections(dialogue),
                    "clause_check": {
                        "score_rules": list(set(re.findall(r"(\+?-?\d+)分", "\n".join([d["output"] for d in dialogue])))),
                        "loan_rules": list(set(re.findall(r"(\d+册|\d+天)", "\n".join([d["output"] for d in dialogue]))))
                    }
                },
                "sections_used": list(set(re.findall(r"$$书籍推荐$$|$$规则说明$$|$$风险提示$$", "\n".join([d["output"] for d in dialogue]))))
            }
        }

    def _find_missing_sections(self, dialogue: List) -> List[str]:
        required = ["[书籍推荐]", "[规则说明]"]
        present = re.findall(r"$$书籍推荐$$|$$规则说明$$|$$风险提示$$", "\n".join([d["output"] for d in dialogue]))
        return [s for s in required if s not in present]

def main():
    generator = LibraryDataGenerator()
    dataset = []
    
    # 生成10组数据
    for _ in range(10):
        data = generator.generate_dialogue()
        if data:
            dataset.append(data)
    
    # 保存结果
    with open("library_dialogue_sync.json", "w", encoding="utf-8") as f:
        json.dump(dataset, f, ensure_ascii=False, indent=2)
        
    print(f"成功生成{len(dataset)}组有效数据")

if __name__ == "__main__":
    main()