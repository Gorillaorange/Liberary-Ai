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

        # 使用统一的Aprompt_template
        self.prompt_template = Aprompt_template  # 直接使用外部模板

    def generate_prompt(self, scenario):
        """使用Aprompt_template生成提示词"""
        return self.prompt_template  # 直接返回完整模板

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
        """修正后的格式化方法"""
        validation_result = self.validate_response(raw_response, scenario)
        return {
            "messages": [
                {"role": "user", "content": f"关于{scenario['name']}的咨询"},
                {"role": "assistant", "content": raw_response}
            ],
            "metadata": {
                "validation": validation_result,  # 添加验证结果
                "sections": [
                    "📚" if "📚" in raw_response else None,
                    "⚠️" if "⚠️" in raw_response else None,
                    "💡" if "💡" in raw_response else None
                ]
            }
        }

    def validate_response(self, response, scenario):
        
        """修正后的校验方法"""
        missing_sections = [
            section for section in scenario["required_sections"]
            if section not in response
        ]
        return {
            "passed": len(missing_sections) == 0,
            "missing_sections": missing_sections
        }
    def log_partial_response(self, formatted_response, scenario, count):
        print(f"\n场景: {scenario['name']} | 进度: {count+1}/30")
        print("-"*50)
        
        content = formatted_response['messages'][1]['content']
        print(f"生成内容预览: {content[:1000]}...")
        
        # 显示区块使用情况
        used_sections = [s for s in ["📚","⚠️","💡"] if s in content]
        print(f"使用区块: {', '.join(used_sections) or '无'}")

    def generate_dataset(self, output_file="library_data3.jsonl", samples_per_scenario=30):
        """修正后的生成逻辑"""
        with open(output_file, "w", encoding="utf-8") as f:
            for scenario in self.scenarios:
                print(f"\n开始生成场景: {scenario['name']}")
                for count in range(samples_per_scenario):
                    prompt = self.generate_prompt(scenario)
                    response = self.call_api(prompt)
                    
                    if response:
                        formatted = self.format_response(response, scenario)
                        self.log_partial_response(formatted, scenario, count)
                        
                        # 修正校验结果访问方式
                        if formatted["metadata"]["validation"]["passed"]:
                            f.write(json.dumps(formatted, ensure_ascii=False) + "\n")





# 统一的提示词模板
Aprompt_template = """生成图书馆服务对话需严格遵循以下规范：
[场景覆盖]
1. 必须覆盖以下至少一个核心场景：
   - 图书推荐（需包含3-5本相关书籍，注明推荐理由）
   - 积分规则咨询（加分/减分具体条款）
   - 违规处理咨询（逾期/损坏/占座等后果）
   - 资源预约（座位/储物柜规则）

[条款嵌入规范]
2. 条款引用要求：
   • 积分规则：
     ➢ 加分项：
       - 借阅图书
       - 提交原创书评(300字+) 
       - 参加活动/课程 
     ➢ 减分项：
       - 逾期(每天每册-5分)
       - 污损图书(最高按10倍赔偿) 
       - 占座违约 

   • 外借规则：
     ➢ 借阅权限：教职工/学生可借30册/60天 
     ➢ 续借规则：可续借2次(需在到期前办理) 
     ➢ 特殊规定：特藏/外文书不外借 

[生成规范]
3. 回复要求：
   - 自然融入2-3个相关条款
   - 使用符号分隔内容：
      书籍推荐部分
      风险提示部分
      规则说明部分
   - 重要数值需精确（如逾期每天扣5分）

[合规校验]
4. 必须规避：
   - 与文档冲突的表述（如外文书可外借）
   - 错误赔偿标准（需按出版年份区分倍数）
   - 过时的续借规则（2022修订版允许线上续借）

示例：
{"messages": [
    {"role": "user", "content": "关于图书推荐的咨询"},
    {"role": "assistant", "content": "📚 量子物理书单推荐：\n1.《量子力学与路径积分》...\n\n💡 借阅小贴士..."}
]}


{"messages": [
  {"role": "user", "content": "毕业前需要做什么准备？"},
  {"role": "assistant", "content": "毕业生离校须知：
1. 归还所有外借图书（含续借中的）
2. 结清逾期费用/赔偿金 
3. 储物柜需在离校前清空
4. 积分可用于毕业季兑换活动 
💡 系统将在离校前3天发送催还提醒 "}
]}

{"messages": [
  {"role": "user", "content": "推荐人工智能入门书籍"},
  {"role": "assistant", "content": "人工智能书单推荐：
1.《人工智能：现代方法》- Stuart Russell
   - 经典教材，涵盖机器学习到伦理问题
2.《Python深度学习》- François Chollet 
   - Keras框架实战指南，适合初学者
3.《机器学习实战》- Peter Harrington
   - 结合算法理论与代码实践

💡 借阅小贴士：
- 每人可同时外借30册 
- 外文书需馆内阅览 
- 撰写300字书评可获积分 "}
]}
[特别说明]
- 用户问题必须围绕具体书籍推荐需求，且user的content随机性非常大，提问问题不重复，不推荐心理学书籍如：
  "推荐人工智能领域的入门书籍"
  "有没有类似《平凡的世界》的现实主义小说"
  "想找适合儿童阅读的科普读物"
- 较低比例出现与书籍推荐无关的问题，如积分规则、借阅流程等
-每次提出的user问题与content标签的内容相符合，并且每次的问题语义不相同,每次的问题随机性大
-如果是遇到确切的符合规范的问题，给予肯定回复，无需回复条例"""

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding='utf-8')  # 设置控制台编码
    generator = LibraryDataGenerator()
    generator.generate_dataset(samples_per_scenario=3000)