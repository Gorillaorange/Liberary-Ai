from datetime import datetime
import os
import sys
import json
import re
import random
from openai import OpenAI
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

class EnhancedGenerator:
    def __init__(self):
        self.valid_count = 0
        self.invalid_count = 0
        
        # API客户端配置
        self.client = OpenAI(
            api_key=os.getenv("API_KEY"),
            base_url=os.getenv("API_BASE", "https://maas-api.cn-huabei-1.xf-yun.com/v1")
        )

        # 场景配置
        self.scenarios = [
            {
                "name": "图书推荐",
                "subtypes": ["计算机", "文学", "历史", "艺术", "科学", "经济"],
                "required_sections": ["[书籍推荐]"]
            },
            {
                "name": "规则咨询", 
                "subtypes": ["借阅", "积分", "违规", "活动", "数字资源"],
                "required_sections": ["[规则说明]"]
            }
        ]
        
        # 动态参数
        self.dynamic_params = {
            "user_roles": ["新生", "研究生", "教授", "访客"],
            "seasonal_events": ["寒假", "毕业季", "开学周", "科研竞赛"]
        }

    def generate_prompt(self, scenario):
        """动态生成带上下文的提示词"""
        subtype = random.choice(scenario["subtypes"])
        user_role = random.choice(self.dynamic_params["user_roles"])
        event = random.choice(self.dynamic_params["seasonal_events"])
        
        return f"""【增强版生成指令】 
请根据以下要求生成图书馆服务对话：
1. 当前场景：{scenario['name']}-{subtype}
2. 用户身份：{user_role}
3. 必须包含 {scenario['required_sections']}
4. 内容需与{event}相关
5. 条款引用需适配{user_role}身份

[格式要求]
使用以下标签组织内容：
- [书籍推荐] 书籍推荐区块
- [规则说明] 规则说明区块
- [风险提示] 风险提示区块

[条款示例]
积分规则：
  • 加分项：借阅(+2/次)、书评(+10/篇)
  • 扣分项：逾期(-5/天)、污损(-20起)
外借规则：
  • 学生可借30册/60天
  • 续借需到期前3天办理
"""

    def call_api(self, prompt):
        """优化后的API调用方法"""
        try:
            response = self.client.chat.completions.create(
                model=os.getenv("SERVICE_ID"),
                messages=[
                    {"role": "system", "content": "专业图书馆咨询服务AI"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.7,
                max_tokens=1024,
                extra_headers={"lora_id": "0"}
            )
            return response.choices[0].message.content
        except Exception as e:
            print(f"API错误: {str(e)}")
            return None

    def format_response(self, raw_response, scenario):
        
        processed = self._process_content(raw_response)
        return {
            "conversation": [{
                "system": "专业图书馆咨询服务AI",
                "input": f"关于{scenario['name']}的咨询",
                "output": processed
            }],
            "metadata": {
                "subtype": scenario["subtypes"][0],
                "user_role": random.choice(self.dynamic_params["user_roles"]),
                "timestamp": datetime.now().isoformat()
            }
        }

    def _process_content(self, content):
        """内容标准化处理"""
        replacements = {
            "📚": "[书籍推荐]",
            "💡": "[规则说明]",
            "⚠️": "[风险提示]"
        }
        for symbol, tag in replacements.items():
            content = content.replace(symbol, tag)
        return content.strip()

    def validate_response(self, response, scenario):
        """严格验证方法"""
        content = response['conversation'][0]['output']
        
        # 标签验证
        missing_tags = [tag for tag in scenario["required_sections"] if tag not in content]
        if missing_tags:
            return {"passed": False, "reason": f"缺少必要标签: {', '.join(missing_tags)}"}
        
        # 内容验证
        if scenario["name"] == "图书推荐":
            if len(re.findall(r"《(.*?)》", content)) < 3:
                return {"passed": False, "reason": "推荐书籍不足3本"}
        elif scenario["name"] == "规则咨询":
            if not re.search(r"\d+", content):
                return {"passed": False, "reason": "未包含具体数值说明"}
        
        return {"passed": True}

    def log_progress(self, response, scenario, count):
        """增强日志记录"""
        metadata = response["metadata"]
        preview = response['conversation'][0]['output'][:1000].replace('\n', ' ')
        print(f"[{metadata['subtype']}] {metadata['user_role']} | 进度: {count+1}/500")
        print(f"生成内容: {preview}...")

    def generate_dataset(self, output_file="dataset2.jsonl"):
        """优化后的数据集生成方法"""
        with open(output_file, "w", encoding="utf-8") as f:
            for scenario in self.scenarios:
                for subtype in scenario["subtypes"]:
                    print(f"\n生成场景: {scenario['name']} - {subtype}")
                    sub_scenario = {
                        "name": f"{scenario['name']}-{subtype}",
                        "required_sections": scenario["required_sections"],
                        "subtypes": [subtype]
                    }
                    
                    for count in range(1000):  # 每个子类型500条
                        prompt = self.generate_prompt(sub_scenario)
                        response = self.call_api(prompt)
                        
                        if not response:
                            self.invalid_count += 1
                            continue
                            
                        formatted = self.format_response(response, sub_scenario)
                        validation = self.validate_response(formatted, sub_scenario)
                        
                        if validation["passed"]:
                            f.write(json.dumps(formatted, ensure_ascii=False) + "\n")
                            self.valid_count += 1
                            self.log_progress(formatted, sub_scenario, count)
                        else:
                            self.invalid_count += 1

        print(f"\n生成完成 | 有效数据: {self.valid_count} | 无效数据: {self.invalid_count}")

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding='utf-8')
    generator = EnhancedGenerator()
    generator.generate_dataset()