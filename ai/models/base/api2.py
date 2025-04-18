import requests
import json
import time
from datetime import datetime

# 配置参数
API_URL = "http://10.100.1.92:6080/aiforward892043260917186560/generate"  # 根据实际地址修改
TEST_PROMPT = "如何续借图书？"  # 测试用提问内容
TIMEOUT = 30000  # 超时时间(秒)

def print_with_time(message, color_code="\033[0m"):
    """带时间戳的彩色打印"""
    timestamp = datetime.now().strftime("%H:%M:%S")
    print(f"{color_code}[{timestamp}] {message}\033[0m")

def test_stream_api():
    headers = {
        "Content-Type": "application/json",
        "Accept": "text/event-stream"  # 重要：声明接受事件流
    }
    
    payload = {
        "text": TEST_PROMPT,
        "max_new_tokens": 256,
        "temperature": 0.7
    }

    full_response = ""
    try:
        with requests.post(API_URL, 
                         json=payload, 
                         headers=headers, 
                         stream=True, 
                         timeout=TIMEOUT) as response:
            
            # 检查HTTP状态码
            if response.status_code != 200:
                print_with_time(f"请求失败: {response.status_code}", "\033[91m")
                return

            print_with_time("开始接收流式响应...", "\033[92m")
            
            # 实时处理数据流
            for line in response.iter_lines():
                if line:
                    # 解析SSE格式数据
                    decoded_line = line.decode('utf-8')
                    if decoded_line.startswith("data: "):
                        event_data = decoded_line[6:]  # 去除"data: "前缀
                        
                        # 处理结束标记
                        if event_data.strip() == "[DONE]":
                            print_with_time("流式传输完成", "\033[93m")
                            break
                            
                        try:
                            data = json.loads(event_data)
                            delta = data.get("delta", "")
                            
                            # 实时显示增量内容
                            if delta:
                                print_with_time(f"收到数据块: {delta}", "\033[94m")
                                full_response += delta
                                
                        except json.JSONDecodeError:
                            print_with_time(f"解析失败: {decoded_line}", "\033[91m")

    except requests.exceptions.RequestException as e:
        print_with_time(f"请求异常: {str(e)}", "\033[91m")
    except KeyboardInterrupt:
        print_with_time("用户中断", "\033[93m")
    finally:
        print_with_time("\n完整响应内容:", "\033[1m")
        print(full_response)

if __name__ == "__main__":
    test_stream_api()