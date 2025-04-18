# AI 书籍推荐服务

这个项目提供了一个基于FastAPI的AI书籍推荐服务，使用DeepSeek模型进行向量搜索和相似度计算。

## 项目结构

```
ai/models/
├── app/                     # 主应用目录
│   ├── config.py            # 配置类定义
│   ├── main.py              # FastAPI应用主文件
│   ├── requirements.txt     # 依赖要求
│   ├── test_search.py       # 搜索功能测试
│   └── test_embeddings.py   # 嵌入向量测试
└── run.py                   # 启动脚本
```

## 配置说明

配置通过以下类提供：

- `APIConfig`: API路由和端点配置
- `DataConfig`: 数据存储路径配置
- `MilvusConfig`: Milvus数据库配置
- `ModelConfig`: 模型相关配置

## 运行应用

### 安装依赖

```bash
pip install -r app/requirements.txt
```

### 启动服务

使用启动脚本：

```bash
python run.py --host 0.0.0.0 --port 8001 --workers 1
```

或直接使用uvicorn：

```bash
cd ai/models
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

## 故障排除

### 模块导入错误

如果遇到 `ModuleNotFoundError: No module named 'app'` 这样的错误，可能是因为Python无法找到应用模块。运行时确保当前工作目录是`ai/models`，或者使用`run.py`脚本启动。

### 依赖问题

确保已安装所有依赖项：

```bash
pip install -r app/requirements.txt
```

如果安装时出现错误，可以尝试单独安装特定版本的库：

```bash
pip install torch==2.1.2
pip install transformers==4.36.2
pip install pymilvus==2.3.4
pip install unsloth==0.3.0
```

## API端点

- `/aiforward895686727887224832/generate` - 生成文本响应
- `/aiforward895686727887224832/embeddings` - 创建文本嵌入向量
- `/aiforward895686727887224832/search` - 搜索相似文本
- `/aiforward895686727887224832/health` - 健康检查 