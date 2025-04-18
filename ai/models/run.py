#!/usr/bin/env python
"""
AI模型服务启动脚本
此脚本用于启动AI模型的FastAPI服务，处理各种配置和导入问题
"""

import os
import sys
import subprocess
import argparse

def main():
    parser = argparse.ArgumentParser(description='启动AI模型服务')
    parser.add_argument('--host', type=str, default='0.0.0.0', help='主机地址')
    parser.add_argument('--port', type=int, default=8001, help='端口号')
    parser.add_argument('--workers', type=int, default=1, help='工作进程数')
    parser.add_argument('--reload', action='store_true', help='是否自动重载')
    args = parser.parse_args()
    
    # 将当前目录加入Python路径，解决导入问题
    current_dir = os.path.dirname(os.path.abspath(__file__))
    sys.path.insert(0, current_dir)
    
    # 启动服务
    cmd = [
        "uvicorn", 
        "app.main:app", 
        "--host", args.host, 
        "--port", str(args.port),
        "--workers", str(args.workers)
    ]
    
    if args.reload:
        cmd.append("--reload")
        
    print(f"启动命令: {' '.join(cmd)}")
    subprocess.run(cmd)

if __name__ == "__main__":
    main() 