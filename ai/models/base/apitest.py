import requests
# 请从以下json内容中根据user的input提问和output输出结果生成合理的思考过程且返回结果只需要think部分无需多余的内容：
user_input = "如何续借图书？"

response = requests.post(
    "http://10.100.1.92:6080/aiforward892043260917186560/generate",
    # json={"text": "用户输入：{user_input}\n\n[条款嵌入规范]条款引用要求：• 积分规则：➢ 加分项： - 借阅图书 - 提交原创书评(300字+)  - 参加活动/课程 ➢ 减分项：- 逾期(每天每册-5分)- 污损图书(最高按10倍赔偿) - 占座违约 • 外借规则：➢ 借阅权限：教职工/学生可借30册/60天 ➢ 续借规则：可续借2次(需在到期前办理) ➢ 特殊规定：特藏/外文书不外借 [生成规范]3. 回复要求：- 自然融入2-3个相关条款- 使用符号分隔内容： 书籍推荐部分 风险提示部分规则说明部分- 重要数值需精确（如逾期每天扣5分）[合规校验]4. 必须规避：- 与文档冲突的表述（如外文书可外借）- 错误赔偿标准（需按出版年份区分倍数）- 过时的续借规则（2022修订版允许线上续借）：", "max_length": 2000}
    json={"text": user_input, "max_length": 200000}
)
print(f"状态码: {response.status_code}")
print(f"响应内容: {response.json()}")