"""
API适配工具类，用于处理参数转换和响应格式处理
主要作用是使模型API与后端调用接口兼容
"""
from typing import Dict, Any, Optional, Union
import json

class APIAdapter:
    """API适配器 - 处理请求参数和响应格式转换"""
    
    @staticmethod
    def adapt_request(request_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        将后端请求格式转换为模型需要的格式
        
        输入格式 (后端):
        {
            "text": "用户输入",
            "max_length": 2000
        }
        
        输出格式 (模型):
        {
            "prompt": "用户输入",
            "max_tokens": 2000,
            "temperature": 0.7
        }
        """
        adapted_request = {}
        
        # 参数映射转换
        if "text" in request_data:
            adapted_request["prompt"] = request_data["text"]
        
        if "max_length" in request_data:
            adapted_request["max_tokens"] = request_data["max_length"]
            
        # 处理可选的temperature参数
        if "temperature" in request_data:
            adapted_request["temperature"] = request_data["temperature"]
        else:
            adapted_request["temperature"] = 0.7
            
        return adapted_request

  
    @staticmethod
    def format_response(model_output: str) -> Dict[str, Any]:
        """
        将模型输出格式化为后端期望的格式
        
        输入:
        "模型生成的文本"
        
        输出:
        {
            "result": 模型生成的文本"
        """
        return {"result": model_output}
    
    @staticmethod
    def format_error(error_message: str, status_code: int = 500) -> Dict[str, Any]:
        """
        格式化错误响应
        
        输出:
        {
            "error": true,
            "message": "错误信息",
            "status_code": 500
        }
        """
        return {
            "error": True,
            "message": error_message,
            "status_code": status_code
        }



