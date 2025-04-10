import os
import sys
import json
from openai import OpenAI
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

class LibraryDataGenerator:
    def __init__(self):
        # 初始化统计变量
        self.valid_count = 0
        self.invalid_count = 0
        
        # API客户端配置
        self.client = OpenAI(
            api_key=os.getenv("API_KEY"),
            base_url=os.getenv("API_BASE", "https://maas-api.cn-huabei-1.xf-yun.com/v1")
        )

        # 场景配置
        self.scenarios = [
            {"name": "图书推荐", "required_sections": ["[书籍推荐]"]},
            {"name": "积分规则", "required_sections": ["[规则说明]"]},
            {"name": "违规处理", "required_sections": ["[风险提示]"]}
        ]

        # 允许使用的文本标签
        self.allowed_tags = [
            "[书籍推荐]",
            "[规则说明]",
            "[风险提示]"
        ]


    def generate_prompt(self, scenario):
        """生成场景特定的提示词"""
        return f"""{self.prompt_template}
        当前生成场景：{scenario['name']}
        必需包含的区块标签：{'、'.join(scenario['required_sections'])}"""

    def call_api(self, prompt, max_tokens=1024):
        """API调用方法"""
        try:
            response = self.client.chat.completions.create(
                model=os.getenv("SERVICE_ID"),
                messages=[
                    {"role": "system", "content": "你是一个专业的图书馆咨询服务AI"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.7,
                max_tokens=max_tokens,
                extra_headers={"lora_id": "0"}
            )
            return response.choices[0].message.content
        except Exception as e:
            print(f"API调用失败: {str(e)}")
            return None

    def format_response(self, raw_response, scenario):
        """格式化API响应"""
        processed_content = self._process_content(raw_response)
        return {
            "messages": [
                {"role": "user", "content": f"关于{scenario['name']}的咨询"},
                {"role": "assistant", "content": processed_content}
            ]
        }

    def _process_content(self, content):
        """内容处理"""
        # 替换可能存在的旧格式符号
        replacements = {
            "📚": "[书籍推荐]",
            "💡": "[规则说明]",
            "⚠️": "[风险提示]"
        }
        for symbol, tag in replacements.items():
            content = content.replace(symbol, tag)
        return content

    def validate_response(self, response, scenario):
        """验证响应有效性"""
        missing_tags = [
            tag for tag in scenario["required_sections"]
            if tag not in response
        ]
        return {
            "passed": len(missing_tags) == 0,
            "missing": missing_tags
        }

    def log_partial_response(self, formatted_response, scenario, count):
        """记录生成进度"""
        content = formatted_response['messages'][1]['content']
        print(f"\n场景: {scenario['name']} | 进度: {count+1}/3000")
        print("-"*50)
        print(f"生成内容预览: {content[:500]}...")
        
        # 显示使用的标签
        used_tags = [tag for tag in self.allowed_tags if tag in content]
        print(f"使用标签: {', '.join(used_tags) or '无'}")

    def _quality_check(self, data):
        """数据质量检查"""
        content = data['messages'][1]['content']
        # 检查必要标签存在性
        return any(tag in content for tag in self.allowed_tags)

    def generate_dataset(self, output_file="answer.jsonl", samples_per_scenario=3000):
        """生成完整数据集"""
        with open(output_file, "w", encoding="utf-8") as f:
            for scenario in self.scenarios:
                print(f"\n开始生成场景: {scenario['name']}")
                for count in range(samples_per_scenario):
                    prompt = self.generate_prompt(scenario)
                    response = self.call_api(prompt)
                    
                    if not response:
                        continue
                        
                    formatted = self.format_response(response, scenario)
                    
                    # 执行质量检查
                    if not self._quality_check(formatted):
                        self.invalid_count += 1
                        continue
                    
                    self.log_partial_response(formatted, scenario, count)
                    
                    if self.validate_response(formatted['messages'][1]['content'], scenario)["passed"]:
                        f.write(json.dumps(formatted, ensure_ascii=False) + "\n")
                        self.valid_count += 1
                    else:
                        self.invalid_count += 1

        print(f"\n生成完成 | 有效数据: {self.valid_count} | 无效数据: {self.invalid_count}")

# 提示词模板
prompt_template = """生成图书馆服务对话需严格遵循以下规范：

[格式要求]
1. 使用以下文本标签分隔内容：
   - [书籍推荐] 书籍推荐区块
   - [规则说明] 规则说明区块
   - [风险提示] 风险提示区块

2. 回复结构：
   以自然对话形式开始
   按需使用上述标签组织内容
   以<answer>结尾
[条款嵌入规范]
2. 条款引用要求：
   积分规则：
      加分项：
       - 借阅图书
       - 提交原创书评(300字+) 
       - 参加活动/课程 
    减分项：
       - 逾期(每天每册-5分)
       - 污损图书(最高按10倍赔偿) 
       - 占座违约 
    外借规则：
     借阅权限：教职工/学生可借30册/60天 
     续借规则：可续借2次(需在到期前办理) 
     特殊规定：特藏/外文书不外借 
[内容规范]
3. 书籍推荐要求：
   - 每次推荐3-5本书籍
   - 包含不同学科领域
   - 注明推荐理由
   自然融入相关条款且简短

4. 规则说明要求：
   - 准确引用最新条款
   - 包含具体数值（如每天扣5分）
   - 说明执行流程

[示例]
用户问：推荐机器学习书籍
助理答：
机器学习领域的经典著作：
[书籍推荐]
1.《机器学习实战》- 包含Scikit-learn案例
2.《深度学习》- Ian Goodfellow经典著作

[规则说明]
- 可续借2次
- 撰写书评可获额外积分
<answer>

用户问：图书损坏如何处理？
助理答：
[风险提示]
1. 立即到服务台登记
2. 按污损程度赔偿：
   - 轻度污损：书价50%
   - 严重损坏：按遗失规则

[规则说明]
赔偿金可用积分抵扣
<answer>"""

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding='utf-8')
    generator = LibraryDataGenerator()
    generator.prompt_template = prompt_template  # 注入模板
    generator.generate_dataset()