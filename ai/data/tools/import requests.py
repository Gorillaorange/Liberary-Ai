import requests
import json
from openai import OpenAI

def generate_data(prompt_template):
    headers = {"Authorization": "Bearer <sk-oZh7PkgH9keSz8S1Fc5cE21dD63f494eB5D01287Fc241c8d>"}
    data = {
        "model": "deepseek-r1",
        "messages": [{"role": "user", "content": prompt_template}],
        "temperature": 0.7
    }
    response = requests.post("https://maas-api.cn-huabei-1.xf-yun.com/v1", 
                           json=data, 
                           headers=headers)
    # 检查响应状态码
    if response.status_code != 200:
        raise Exception(f"API请求失败，状态码：{response.status_code}")
    
    # 打印完整的响应内容
    response_json = response.json()
    print("API响应内容：", response_json)
    
    # 检查 'choices' 键是否存在
    if "choices" not in response_json:
        raise KeyError("'choices' 键不存在于响应中")
    
    return response_json["choices"][0]["message"]["content"]


prompt_template = """生成图书馆服务对话需严格遵循以下规范：
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
       - 借阅图书 [条款1.1]
       - 提交原创书评(300字+) [条款1.3]
       - 参加活动/课程 [条款1.4/1.5]
     ➢ 减分项：
       - 逾期(每天每册-5分) [条款2.1+文档2.III]
       - 污损图书(最高按10倍赔偿) [条款2.2+文档2.IV]
       - 占座违约 [条款2.4]

   • 外借规则：
     ➢ 借阅权限：教职工/学生可借30册/60天 [条款3.2]
     ➢ 续借规则：可续借2次(需在到期前办理) [条款4.2]
     ➢ 特殊规定：特藏/外文书不外借 [条款3.4]

[生成规范]
3. 回复要求：
   - 自然融入2-3个相关条款（每个条款单独标注）
   - 使用符号分隔内容：
     📚 书籍推荐部分
     ⚠️ 风险提示部分
     💡 规则说明部分
   - 引用格式：[条款编号](@ref) 包含文档序号（例：[1.3]指文档1第3条）
   - 重要数值需精确（如逾期每天扣5分）

[合规校验]
4. 必须规避：
   - 与文档冲突的表述（如外文书可外借）
   - 错误赔偿标准（需按出版年份区分倍数）
   - 过时的续借规则（2022修订版允许线上续借）

示例：
{"messages": [
  {"role": "user", "content": "不小心把咖啡洒在书上了怎么办？"},
  {"role": "assistant", "content": "⚠️ 图书污损处理流程：
1. 请立即到服务台登记情况
2. 根据污损程度处理：
   - 轻度污损（如页面水渍）：按书价50%赔偿 [2.2.1](@ref)
   - 严重污损（影响阅读）：按遗失规则赔偿 [2.2.2](@ref)
   - 损坏磁条/条码：需额外支付加工费 [2.2.3](@ref)
💡 温馨提示：外借时请检查图书状态，发现问题及时报备可免除责任 [3.3](@ref)"}
]}

{"messages": [
  {"role": "user", "content": "毕业前需要做什么准备？"},
  {"role": "assistant", "content": "毕业生离校须知：
1. 归还所有外借图书（含续借中的）[4.4](@ref)
2. 结清逾期费用/赔偿金 [文档2.IV]
3. 储物柜需在离校前清空
4. 积分可用于毕业季兑换活动 [条款1.12]
💡 系统将在离校前3天发送催还提醒 [5](@ref)"}
]}

{"messages": [
  {"role": "user", "content": "推荐人工智能入门书籍"},
  {"role": "assistant", "content": "📚 人工智能书单推荐：
1.《人工智能：现代方法》- Stuart Russell
   - 经典教材，涵盖机器学习到伦理问题
2.《Python深度学习》- François Chollet 
   - Keras框架实战指南，适合初学者
3.《机器学习实战》- Peter Harrington
   - 结合算法理论与代码实践

💡 借阅小贴士：
- 每人可同时外借30册 [3.2](@ref)
- 外文书需馆内阅览 [3.4](@ref)
- 撰写300字书评可获积分 [1.3](@ref)"}
]}"""

# 新增的保存JSON功能函数
def save_to_json(data, filename='library_dialogs.json'):
    """
    将生成的对话数据保存为JSON文件
    
    参数：
    data: str - 包含多个JSON对象的字符串
    filename: str - 输出文件名（默认：library_dialogs.json）
    """
    try:
        # 分割并解析生成的JSON对象
        dialogues = [json.loads(obj) for obj in data.strip().split('\n') if obj]
        
        # 写入JSON文件
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(dialogues, f, 
                     ensure_ascii=False,  # 保留中文
                     indent=4,           # 格式化缩进
                     separators=(',', ': '))  # 优化分隔符
            
        print(f"✅ 数据已保存至 {filename}")
        print(f"📂 文件路径：{os.path.abspath(filename)}")
        
    except json.JSONDecodeError as e:
        print(f"❌ JSON解析失败：{e}")
    except Exception as e:
        print(f"❌ 文件保存异常：{e}")

# 在原有代码后添加调用
generated_data = generate_data(prompt_template)
save_to_json(generated_data)  # 新增这行保存操作