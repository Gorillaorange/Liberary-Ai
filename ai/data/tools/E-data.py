import json
from openai import OpenAI
import time

client = OpenAI(
    api_key="sk-PQj8lgRQzljlnhhzgj5Mf3kKQs8bM0l2bjgnF8sBGpALtN7F",
    base_url="https://api.lkeap.cloud.tencent.com/v1",
)

def save_raw_json(content, filename="library_data.jsonl"):
    """直接保存原始JSON内容"""
    with open(filename, "a", encoding="utf-8") as f:
        f.write(content.strip() + "\n")

class StreamHandler:
    def __init__(self):
        self.json_buffer = ""
        self.brace_count = 0
        self.generated_count = 0

    def process_stream(self, prompt):
        stream = client.chat.completions.create(
            model="deepseek-v3",
            messages=[{"role": "user", "content": prompt}],
            stream=True,
            max_tokens=2000,
            temperature=0.7
        )

        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                content = chunk.choices[0].delta.content
                print(content, end='', flush=True)  # 实时显示流内容
                
                # 更新JSON缓冲
                self._update_json_buffer(content)

                # 检测完整JSON对象
                if self._is_complete_json():
                    self._save_and_reset()

    def _update_json_buffer(self, content):
        """维护JSON括号计数"""
        self.json_buffer += content
        self.brace_count += content.count('{')
        self.brace_count -= content.count('}')

    def _is_complete_json(self):
        """验证JSON完整性"""
        return self.brace_count == 0 and self.json_buffer.startswith('{')

    def _save_and_reset(self):
        """保存并重置状态"""
        try:
            # # 验证JSON有效性
            # json.loads(self.json_buffer)
            
            # 直接保存原始内容
            print(self.json_buffer)
            save_raw_json(self.json_buffer)
            self.generated_count += 1
            print(f"\n已保存第{self.generated_count}条数据\n")
            
        # except json.JSONDecodeError:
        #     print("\n⚠️ 无效JSON已丢弃")
        finally:
            self.json_buffer = ""
            self.brace_count = 0

# 使用示例
if __name__ == "__main__":
    handler = StreamHandler()
    prompt = """我需要你根据我的现有数据样本，生成类似的可以用于微调的数据样本，不用回答多余的语句，直接生成数据100条，主要是一些问答数据，但是要求为不能重复，并且又提示词规则限制（生成图书馆服务对话需严格遵循以下规范： 

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
赔偿金可用积分抵扣） ，以及生成的数据需要自己校验，不能存在重复的提问数据生成，然后我将提供给你部分已经可以用于微调的数据，输出格式要与其匹配：{"conversation": [{"system": "你是一个专业的图书馆咨询服务AI", "input": "关于图书推荐的咨询", "output": "用户问：我刚读完《百年孤独》，有没有类似的好书推荐？\n\n助理答：\n当然可以！《百年孤独》是一部经典的魔幻现实主义作品，以下是一些风格相似或主题相近的书籍推荐：\n\n[书籍推荐]\n1. 《霍乱时期的爱情》- 加西亚·马尔克斯的另一部杰作，探讨了爱情与时间的主题，文笔同样优美且充满魔幻色彩。\n2. 《小径分岔的花园》- 豪尔赫·路易斯·博尔赫斯的短篇小说集，充满了哲学思考和迷宫般的叙事结构，适合喜欢复杂叙事的读者。\n3. 《佩德罗·巴拉莫》- 胡安·鲁尔福的代表作，通过非线性的叙事和魔幻现实主义的手法，讲述了一个鬼魂村庄的故事。\n4. 《大师与玛格丽特》- 米哈伊尔·布尔加科夫的作品，融合了现实主义与超自然元素，探讨了善恶、艺术与权力的主题。\n5. 《午夜的孩子》- 萨尔曼·鲁西迪的小说，通过魔幻现实主义的手法讲述了印度独立后的历史与个人命运的交织。\n\n[规则说明]\n- 这些书籍都可以在图书馆借阅，教职工和学生可借30册，借阅期限为60天。\n- 如果需要，每本书可续借2次，但需在到期前办理续借手续。\n\n希望这些推荐能帮助你找到下一本喜欢的书！如果有其他需求，随时告诉我。 "}]}
{"conversation": [{"system": "你是一个专业的图书馆咨询服务AI", "input": "关于图书推荐的咨询", "output": "用户问：我最近对人工智能很感兴趣，有没有相关的入门书籍推荐？\n\n助理答：\n当然可以！以下是一些关于人工智能的入门书籍推荐，它们将帮助你更好地理解这个领域的基础知识和最新进展。\n\n[书籍推荐]\n1. 《人工智能：一种现代的方法》- 作者：Stuart Russell, Peter Norvig\n   - 推荐理由：这本书是人工智能领域的经典教材，涵盖了从基础知识到高级技术的广泛内容，适合初学者和进阶读者。\n\n2. 《深度学习》- 作者：Ian Goodfellow, Yoshua Bengio, Aaron Courville\n   - 推荐理由：深度学习是人工智能的一个重要分支，本书详细介绍了深度学习的理论基础和实践应用，是学习深度学习的必读书籍。\n\n3. 《机器学习实战》- 作者：Peter Harrington\n   - 推荐理由：通过实际案例和代码示例，本书帮助读者理解机器学习的基本概念和应用，适合希望通过实践学习的读者。\n\n4. 《Python机器学习》- 作者：Sebastian Raschka, Vahid Mirjalili\n   - 推荐理由：本书结合Python编程语言，介绍了机器学习的基本算法和实现方法，适合有一定编程基础的读者。\n\n5. 《人工智能简史》- 作者：尼克\n   - 推荐理由：本书以通俗易懂的语言介绍了人工智能的发展历程和重要里程碑，适合对人工智能历史感兴趣的读者。\n\n[规则说明]\n- 借阅权限：教职工/学生可借30册/60天\n- 续借规则：可续借2次(需在到期前办理)\n- 撰写书评可获额外积分\n\n希望这些书籍能帮助你更好地理解人工智能领域。如果你有任何其他问题或需要进一步的推荐，请随时联系我。 "}]}
{"conversation": [{"system": "你是一个专业的图书馆咨询服务AI", "input": "关于图书推荐的咨询", "output": "用户问：我刚读完《三体》，有没有类似的好书推荐？\n\n助理答：\n很高兴听到你对科幻小说感兴趣！以下是一些与《三体》风格相似的优秀科幻作品推荐：\n\n[书籍推荐]\n1.《银河帝国》系列 - 艾萨克·阿西莫夫\n   - 推荐理由：被誉为科幻小说的基石，探讨了人类文明的未来与机器人的关系，与《三体》一样具有宏大的宇宙观。\n2.《黑暗森林》 - 刘慈欣\n   - 推荐理由：这是《三体》系列的第二部，深入探讨了宇宙中的生存法则和文明的黑暗面。\n3.《沙丘》 - 弗兰克·赫伯特\n   - 推荐理由：经典科幻小说，讲述了一个复杂的星际政治和生态系统的故事，与《三体》一样具有深刻的思想性。\n4.《基地》 - 艾萨克·阿西莫夫\n   - 推荐理由：另一部阿西莫夫的经典作品，讲述了银河帝国衰落和基地建立的故事，充满了历史感和哲学思考。\n5.《超新星纪元》 - 刘慈欣\n   - 推荐理由：刘慈欣的另一部作品，探讨了人类在面临灭绝危机时的选择和未来。\n\n[规则说明]\n- 借阅权限：教职工/学生可借30册/60天\n- 续借规则：可续借2次(需在到期前办理)\n- 特殊规定：特藏/外文书不外借\n\n希望这些推荐能带给你更多的阅读乐趣！如果你有其他问题或需要进一步的帮助，随时告诉我。 "}]}
{"conversation": [{"system": "你是一个专业的图书馆咨询服务AI", "input": "关于图书推荐的咨询", "output": "用户问：我刚读完《人工智能：一种现代的方法》，有没有类似的好书推荐？\n\n助理答：当然可以！以下是几本与人工智能相关的书籍，可能会符合你的兴趣：\n\n[书籍推荐]\n1. 《深度学习》 by Ian Goodfellow, Yoshua Bengio, and Aaron Courville\n   - 推荐理由：这本书是深度学习领域的权威著作，详细介绍了深度学习的理论基础和实际应用，适合有一定基础的读者深入学习。\n\n2. 《模式识别与机器学习》 by Christopher M. Bishop\n   - 推荐理由：这本书系统地介绍了模式识别和机器学习的基本概念和算法，适合希望从理论到实践全面了解机器学习的读者。\n\n3. 《人工智能：一种系统的方法》 by Stuart Russell and Peter Norvig\n   - 推荐理由：这本书是《人工智能：一种现代的方法》的姊妹篇，提供了更多关于人工智能系统设计和实现的内容，适合对人工智能系统感兴趣的读者。\n\n4. 《强化学习》 by Richard S. Sutton and Andrew G. Barto\n   - 推荐理由：这本书是强化学习领域的经典教材，详细介绍了强化学习的基本原理和算法，适合对这一领域感兴趣的读者。\n\n5. 《自然语言处理》 by Daniel Jurafsky and James H. Martin\n   - 推荐理由：这本书全面介绍了自然语言处理的理论和实践，适合对语言处理和文本分析感兴趣的读者。\n\n[规则说明]\n- 借阅权限：教职工/学生可借30册/60天\n- 续借规则：可续借2次(需在到期前办理)\n- 特殊规定：特藏/外文书不外借\n\n希望这些书籍能帮助你进一步探索人工智能的世界！如果有其他问题或需要更多推荐，随时告诉我。 "}]}
{"conversation": [{"system": "你是一个专业的图书馆咨询服务AI", "input": "关于图书推荐的咨询", "output": "用户问：我刚读完《人工智能简史》，有没有类似的好书推荐？\n\n助理答：很高兴听到你对人工智能感兴趣！以下是一些与《人工智能简史》类似的书籍推荐，希望能满足你的阅读需求。\n\n[书籍推荐]\n1. 《人工智能：一种现代的方法》- 由Stuart Russell和Peter Norvig合著，是人工智能领域的经典教材，涵盖了从基础理论到最新技术的广泛内容，适合深入学习和研究。\n2. 《深度学习》- 由Ian Goodfellow、Yoshua Bengio和Aaron Courville合著，详细介绍了深度学习的原理和应用，是理解现代人工智能技术不可或缺的参考书。\n3. 《超级智能：路径、危险与策略》- 由Nick Bostrom撰写，探讨了超级智能可能带来的影响和挑战，适合对人工智能未来发展方向感兴趣的读者。\n4. 《人工智能的未来》- 由Ray Kurzweil撰写，预测了人工智能技术在未来几十年内的发展趋势，以及对人类社会可能产生的影响。\n5. 《机器学习：概率视角》- 由Kevin P. Murphy撰写，从概率论的角度深入讲解了机器学习的核心概念和方法，适合有一定数学基础的读者。\n\n[规则说明]\n- 每本书的借阅期限为60天，可续借2次，需在到期前办理续借手续。\n- 撰写并提交300字以上的原创书评，可获得额外借阅积分。\n\n希望这些书籍能帮助你更深入地了解人工智能领域，享受阅读的乐趣！如果有其他问题或需要进一步的帮助，请随时联系图书馆服务台。 "}]}"""
    
    try:
        while True:  # 持续生成
            print(f"\n正在生成第{handler.generated_count+1}条数据...")
            handler.process_stream("你好啊")
            time.sleep(100000)  # 控制请求频率
    except KeyboardInterrupt:
        print(f"\n生成终止，共生成{handler.generated_count}条数据")