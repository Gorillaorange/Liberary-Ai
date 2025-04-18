from fastapi import FastAPI, Request, HTTPException, Depends
from fastapi.responses import StreamingResponse
from transformers import AutoTokenizer, AutoModel
from unsloth import FastLanguageModel
import uvicorn
import json
import datetime
import torch
import re
import numpy as np
from pydantic import BaseModel
from typing import Optional, List, Dict, Any, Union, Generator
from fastapi.middleware.cors import CORSMiddleware
import traceback
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType, utility
from milvus import default_server
import logging
import sys
import time
import os
from huggingface_hub import HfFolder
from huggingface_hub.utils import HfHubHTTPError
import sqlite3
from pathlib import Path
import importlib.util
import sys
import asyncio

# 定义默认响应提示词
RESPONSE_PROMPT = """你是一个智能AI助手，擅长回答用户关于图书和阅读的各类问题。你的回复需要有礼貌，准确，并且要尽可能有帮助。"""

# 定义配置类 (如果导入失败时使用)
class _APIConfig:
    PREFIX = "aiforward898236585550544896"
    GENERATE = "/generate"
    EMBEDDINGS = "/embeddings"
    SEARCH = "/search"
    HEALTH = "/health"
    ANALYZE_TYPE = "/analyze_type"
    
    @classmethod
    def get_path(cls, endpoint: str) -> str:
        return f"/{cls.PREFIX}{endpoint}"

class _DataConfig:
    DATA_DIR = Path("db/data_dir")
    EMBEDDINGS_FILE = DATA_DIR / "embeddings.json"
    DB_FILE = DATA_DIR / "embeddings.db"
    
    @classmethod
    def ensure_data_dir(cls):
        cls.DATA_DIR.mkdir(parents=True, exist_ok=True)

class _MilvusConfig:
    HOST = '127.0.0.1'
    COLLECTION_NAME = "book_embeddings"
    VECTOR_DIMENSION = 1536
    SEARCH_PARAMS = {
        "metric_type": "L2",
        "params": {
            "nprobe": 1024,
            "radius": 2000.0,
            "range_filter": 0.0
        }
    }
    SIMILARITY_THRESHOLD = 0.3
    INDEX_PARAMS = {
        "metric_type": "L2",
        "index_type": "IVF_FLAT",
        "params": {
            "nlist": 1024,
            "nprobe": 16
        }
    }

class _ModelConfig:
    DEVICE = "cuda"
    DEVICE_ID = "0"
    CUDA_DEVICE = f"{DEVICE}:{DEVICE_ID}" if DEVICE_ID else DEVICE
    LOCAL_MODEL_PATH = "/public/home/ssjxhxy/my_models/deepseek-14b"
    MAX_SEQ_LENGTH = 4096
    LOAD_IN_4BIT = True

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

# 尝试从配置文件导入，如果失败则使用默认配置
try:
    from config import APIConfig, DataConfig, MilvusConfig, ModelConfig
    logger.info("成功从配置文件导入配置类")
except ImportError:
    APIConfig = _APIConfig
    DataConfig = _DataConfig
    MilvusConfig = _MilvusConfig
    ModelConfig = _ModelConfig
    logger.warning("无法从配置文件导入，使用内联配置")

# 配置 Hugging Face 镜像和认证
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
HfFolder.save_token('your_token_here')  # 替换为你的 Hugging Face token

# 确保数据目录存在
DataConfig.ensure_data_dir()

def init_milvus():
    """初始化Milvus服务"""
    try:
        # 设置数据目录
        data_dir = "./milvus_data"
        os.makedirs(data_dir, exist_ok=True)
        default_server.set_base_dir(data_dir)
        
        # 启动Milvus服务
        default_server.start()
        time.sleep(5)  # 等待服务完全启动
        
        # 获取动态端口
        milvus_port = default_server.listen_port
        logger.info(f"Milvus服务运行在端口: {milvus_port}")
        
        # 连接Milvus
        connections.connect(host=MilvusConfig.HOST, port=milvus_port)
        logger.info("Successfully connected to Milvus")
        
        # 创建集合（如果不存在）
        if not utility.has_collection(MilvusConfig.COLLECTION_NAME):
            fields = [
                FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
                FieldSchema(name="book_id", dtype=DataType.INT64),
                FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=MilvusConfig.VECTOR_DIMENSION),
                FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=65535),
                FieldSchema(name="metadata", dtype=DataType.JSON),
                FieldSchema(name="created_at", dtype=DataType.INT64)
            ]
            schema = CollectionSchema(fields=fields, description="Book embeddings collection")
            collection = Collection(name=MilvusConfig.COLLECTION_NAME, schema=schema)
            
            # 创建索引
            collection.create_index(field_name="embedding", index_params=MilvusConfig.INDEX_PARAMS)
            logger.info(f"Created collection: {MilvusConfig.COLLECTION_NAME}")
        else:
            collection = Collection(name=MilvusConfig.COLLECTION_NAME)
            # 检查集合状态
            collection.load()
            num_entities = collection.num_entities
            logger.info(f"Collection {MilvusConfig.COLLECTION_NAME} has {num_entities} entities")
            
            # 检查索引状态
            if not collection.has_index():
                logger.warning("Collection exists but has no index, creating index...")
                collection.create_index(field_name="embedding", index_params=MilvusConfig.INDEX_PARAMS)
            else:
                index_info = collection.index()
                logger.info(f"Collection index info: {index_info}")
            
            logger.info(f"Using existing collection: {MilvusConfig.COLLECTION_NAME}")
        
        return collection
    except Exception as e:
        logger.error(f"Failed to initialize Milvus: {e}")
        raise

def cleanup_milvus():
    """清理Milvus服务"""
    try:
        connections.disconnect("default")
        default_server.stop()
        logger.info("Milvus service stopped")
    except Exception as e:
        logger.error(f"Error stopping Milvus: {e}")

def load_model_with_retry(model_name_or_path, max_retries=3, retry_delay=5):
    """带重试机制的模型加载函数"""
    for attempt in range(max_retries):
        try:
            logger.info(f"尝试加载模型 (第 {attempt + 1} 次)...")
            tokenizer = AutoTokenizer.from_pretrained(
                model_name_or_path,
                use_fast=False,
                local_files_only=attempt > 0  # 第一次尝试下载，后续尝试使用本地文件
            )
            model, adapter = FastLanguageModel.from_pretrained(
                model_name=model_name_or_path,
                max_seq_length=ModelConfig.MAX_SEQ_LENGTH,
                load_in_4bit=ModelConfig.LOAD_IN_4BIT,
                local_files_only=attempt > 0
            )
            logger.info("模型加载成功！")
            return model, tokenizer, adapter
        except HfHubHTTPError as e:
            if attempt < max_retries - 1:
                logger.warning(f"模型加载失败，{retry_delay}秒后重试...")
                time.sleep(retry_delay)
            else:
                logger.error("模型加载失败，已达到最大重试次数")
                raise
        except Exception as e:
            logger.error(f"模型加载时发生错误: {e}")
            raise

# 初始化模型
try:
    logger.info(f"正在加载本地模型: {ModelConfig.LOCAL_MODEL_PATH}")
    
    tokenizer = AutoTokenizer.from_pretrained(
        ModelConfig.LOCAL_MODEL_PATH,
        use_fast=False,
        local_files_only=True  # 强制使用本地文件
    )
    
    model, adapter = FastLanguageModel.from_pretrained(
        model_name=ModelConfig.LOCAL_MODEL_PATH,
        max_seq_length=ModelConfig.MAX_SEQ_LENGTH,
        load_in_4bit=ModelConfig.LOAD_IN_4BIT,
        local_files_only=True  # 强制使用本地文件
    )
    
    logger.info("本地模型加载成功！")
except Exception as e:
    logger.error(f"本地模型加载失败: {e}")
    raise

# 初始化FastAPI应用
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化Milvus集合
try:
    collection = init_milvus()
except Exception as e:
    logger.error(f"Failed to initialize Milvus: {e}")
    sys.exit(1)

def torch_gc():
    if torch.cuda.is_available():
        with torch.cuda.device(ModelConfig.CUDA_DEVICE):
            torch.cuda.empty_cache()
            torch.cuda.ipc_collect()

def split_text(text):
    pattern = re.compile(r'<think>(.*?)(.*)', re.DOTALL)
    match = pattern.search(text)
  
    if match:
        think_content = match.group(1).strip()
        answer_content = match.group(2).strip()
    else:
        think_content = ""
        answer_content = text.strip()
  
    return think_content, answer_content

class GenerateRequest(BaseModel):
    text: str
    system_prompt: Optional[str] = None
    max_new_tokens: int = ModelConfig.MAX_SEQ_LENGTH
    temperature: float = 0.6

class EmbeddingRequest(BaseModel):
    text: str
    metadata: Optional[Dict[str, Any]] = None

class EmbeddingResponse(BaseModel):
    data: List[Dict[str, Any]]
    model: str
    usage: Dict[str, int]

class SearchRequest(BaseModel):
    query: str
    limit: int = 5
    offset: int = 0

@app.post(APIConfig.get_path(APIConfig.GENERATE))
async def create_item(request: Request):
    json_post_raw = await request.json()
    json_post = json.dumps(json_post_raw)
    json_post_list = json.loads(json_post)
    
    # 获取系统提示词和用户提示
    system_prompt = json_post_list.get('system_prompt')
    user_prompt = json_post_list.get('text', '')
    
    # 构建消息列表
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    if user_prompt:
        messages.append({"role": "user", "content": user_prompt})
    
    # 生成对话模板
    input_ids = tokenizer.apply_chat_template(
        messages,
        tokenize=False,
        add_generation_prompt=True
    )
    
    try:
        # 记录开始时间
        start_time = time.time()
        
        # 生成响应
        model_inputs = tokenizer([input_ids], return_tensors="pt").to(model.device)
        
        # 使用最原始的生成方法，不做特殊处理
        with torch.no_grad():
            outputs = model.generate(
                model_inputs.input_ids,
                max_new_tokens=ModelConfig.MAX_SEQ_LENGTH,
                do_sample=True,
                temperature=0.7
            )
            
            # 获取生成的token
            input_length = model_inputs.input_ids.shape[1]
            generated_tokens = outputs[0][input_length:]
            
            # 解码生成的token
            complete_text = tokenizer.decode(generated_tokens, skip_special_tokens=True)
            
            # 记录结束时间
            end_time = time.time()
            elapsed_time = end_time - start_time
            logger.info(f"生成完成，耗时: {elapsed_time:.2f}秒")
            
            # 保留原始文本，不做任何处理
            # response = {
            #     "raw_text": "<think>"+complete_text,
            #     "time_taken": elapsed_time
            # }
            response = "<think>"+complete_text;
            
            # 清理资源
            torch_gc()
            
            return response
            
    except Exception as e:
        logger.error(f"生成过程中发生错误: {e}")
        traceback.print_exc()
        raise HTTPException(
            status_code=500,
            detail=f"生成过程中发生错误: {str(e)}"
        )

def get_text_embedding(text: str) -> List[float]:
    """使用DeepSeek模型生成文本的embedding向量"""
    try:
        # 对输入文本进行编码
        inputs = tokenizer(
            text,
            return_tensors="pt",
            max_length=512,
            truncation=True,
            padding=True,
            add_special_tokens=True
        ).to(model.device)

        # 获取模型输出
        with torch.no_grad():
            outputs = model(**inputs, output_hidden_states=True)
            
            # 使用最后一层隐藏状态的平均值作为embedding
            last_hidden_states = outputs.hidden_states[-1]
            attention_mask = inputs["attention_mask"]
            
            # 扩展attention mask用于矩阵计算
            mask = attention_mask.unsqueeze(-1).expand(last_hidden_states.size()).float()
            sum_embeddings = torch.sum(last_hidden_states * mask, 1)
            sum_mask = torch.clamp(mask.sum(1), min=1e-9)
            embeddings = sum_embeddings / sum_mask
            
            # 确保维度正确
            if embeddings.size(1) != MilvusConfig.VECTOR_DIMENSION:
                # 如果维度不匹配，使用线性投影调整维度
                projection = torch.nn.Linear(embeddings.size(1), MilvusConfig.VECTOR_DIMENSION).to(model.device)
                embeddings = projection(embeddings)
            
            # 转换为numpy数组并归一化
            embeddings_np = embeddings.cpu().numpy()
            norms = np.linalg.norm(embeddings_np, axis=1, keepdims=True)
            embeddings_norm = embeddings_np / norms
            
            # 确保返回的向量维度正确
            embedding_vector = embeddings_norm[0].tolist()
            if len(embedding_vector) != MilvusConfig.VECTOR_DIMENSION:
                raise ValueError(f"生成的向量维度不正确: {len(embedding_vector)} != {MilvusConfig.VECTOR_DIMENSION}")
            
            # 记录向量信息
            logger.info(f"Generated embedding vector with dimension: {len(embedding_vector)}")
            logger.info(f"Vector norm: {np.linalg.norm(embedding_vector)}")
            
            return embedding_vector
    except Exception as e:
        logger.error(f"生成embedding失败: {e}")
        raise HTTPException(status_code=500, detail=f"生成embedding失败: {str(e)}")

def init_db():
    """初始化SQLite数据库"""
    conn = sqlite3.connect(DataConfig.DB_FILE)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS embeddings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER,
            text TEXT,
            embedding BLOB,
            metadata TEXT,
            created_at INTEGER
        )
    ''')
    conn.commit()
    conn.close()

def save_to_file(data: dict):
    """保存数据到JSON文件"""
    try:
        # 读取现有数据
        if DataConfig.EMBEDDINGS_FILE.exists():
            with open(DataConfig.EMBEDDINGS_FILE, 'r', encoding='utf-8') as f:
                existing_data = json.load(f)
        else:
            existing_data = []
        
        # 添加新数据
        existing_data.append(data)
        
        # 保存更新后的数据
        with open(DataConfig.EMBEDDINGS_FILE, 'w', encoding='utf-8') as f:
            json.dump(existing_data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        logger.error(f"保存到文件失败: {e}")

def save_to_db(data: dict):
    """保存数据到SQLite数据库"""
    try:
        conn = sqlite3.connect(DataConfig.DB_FILE)
        cursor = conn.cursor()
        
        # 准备数据
        embedding_blob = json.dumps(data['embedding']).encode('utf-8')
        metadata_json = json.dumps(data['metadata'])
        
        # 插入数据
        cursor.execute('''
            INSERT INTO embeddings (book_id, text, embedding, metadata, created_at)
            VALUES (?, ?, ?, ?, ?)
        ''', (
            data['book_id'],
            data['text'],
            embedding_blob,
            metadata_json,
            data['created_at']
        ))
        
        conn.commit()
        conn.close()
    except Exception as e:
        logger.error(f"保存到数据库失败: {e}")

@app.post(APIConfig.get_path(APIConfig.EMBEDDINGS))
async def create_embedding(request: EmbeddingRequest):
    try:
        # 生成文本的embedding向量
        embedding = get_text_embedding(request.text)
        
        # 验证向量维度
        if len(embedding) != MilvusConfig.VECTOR_DIMENSION:
            raise ValueError(f"向量维度不正确: {len(embedding)} != {MilvusConfig.VECTOR_DIMENSION}")
        
        # 准备数据
        data = {
            "book_id": request.metadata.get("book_id") if request.metadata else None,
            "embedding": embedding,
            "text": request.text,
            "metadata": request.metadata or {},
            "created_at": int(datetime.datetime.now().timestamp())
        }
        
        # 插入到Milvus
        collection.insert([data])
        logger.info(f"Successfully inserted embedding for book_id: {data['book_id']}")
        
        # 持久化存储
        save_to_file(data)
        save_to_db(data)
        logger.info("数据持久化存储成功")
        
        return {"status": "success", "message": "Embedding created and persisted successfully"}
    except Exception as e:
        logger.error(f"Error creating embedding: {e}")
        raise HTTPException(status_code=500, detail=str(e))

def load_from_persistent_storage():
    """从持久化存储中加载数据到Milvus"""
    try:
        # 从JSON文件加载
        if DataConfig.EMBEDDINGS_FILE.exists():
            with open(DataConfig.EMBEDDINGS_FILE, 'r', encoding='utf-8') as f:
                json_data = json.load(f)
                if json_data:
                    collection.insert(json_data)
                    logger.info(f"从JSON文件加载了 {len(json_data)} 条数据到Milvus")
        
        # 从SQLite数据库加载
        conn = sqlite3.connect(DataConfig.DB_FILE)
        cursor = conn.cursor()
        cursor.execute("SELECT book_id, text, embedding, metadata, created_at FROM embeddings")
        rows = cursor.fetchall()
        if rows:
            db_data = []
            for row in rows:
                book_id, text, embedding_blob, metadata_json, created_at = row
                embedding = json.loads(embedding_blob.decode('utf-8'))
                metadata = json.loads(metadata_json)
                db_data.append({
                    "book_id": book_id,
                    "text": text,
                    "embedding": embedding,
                    "metadata": metadata,
                    "created_at": created_at
                })
            collection.insert(db_data)
            logger.info(f"从SQLite数据库加载了 {len(db_data)} 条数据到Milvus")
        conn.close()
    except Exception as e:
        logger.error(f"从持久化存储加载数据失败: {e}")

def search_from_persistent_storage(query_embedding: list, limit: int) -> list:
    """从持久化存储中搜索相似数据"""
    results = []
    try:
        # 从JSON文件搜索
        if DataConfig.EMBEDDINGS_FILE.exists():
            with open(DataConfig.EMBEDDINGS_FILE, 'r', encoding='utf-8') as f:
                json_data = json.load(f)
                for item in json_data:
                    similarity = 1.0 / (1.0 + np.linalg.norm(np.array(query_embedding) - np.array(item['embedding'])))
                    if similarity >= 0.05:  # 降低相似度阈值
                        results.append({
                            "id": item.get('book_id'),
                            "score": similarity,
                            "book_id": item.get('book_id'),
                            "text": item.get('text'),
                            "metadata": item.get('metadata')
                        })
        
        # 从SQLite数据库搜索
        conn = sqlite3.connect(DataConfig.DB_FILE)
        cursor = conn.cursor()
        cursor.execute("SELECT book_id, text, embedding, metadata FROM embeddings")
        for row in cursor.fetchall():
            book_id, text, embedding_blob, metadata_json = row
            embedding = json.loads(embedding_blob.decode('utf-8'))
            similarity = 1.0 / (1.0 + np.linalg.norm(np.array(query_embedding) - np.array(embedding)))
            if similarity >= 0.05:  # 降低相似度阈值
                results.append({
                    "id": book_id,
                    "score": similarity,
                    "book_id": book_id,
                    "text": text,
                    "metadata": json.loads(metadata_json)
                })
        conn.close()
        
        # 按分数排序并限制结果数量
        results.sort(key=lambda x: x["score"], reverse=True)
        return results[:limit]
    except Exception as e:
        logger.error(f"从持久化存储搜索失败: {e}")
        return []

@app.post(APIConfig.get_path(APIConfig.SEARCH))
async def search_similar(request: SearchRequest):
    try:
        # 生成查询文本的embedding向量
        query_embedding = get_text_embedding(request.query)
        logger.info(f"Generated query embedding for: {request.query}")
        
        # 用于存储已处理的book_id，避免重复
        processed_book_ids = set()
        
        # 尝试从Milvus搜索
        try:
            # 加载集合
            collection.load()
            logger.info(f"Collection loaded, total entities: {collection.num_entities}")
            
            # 执行搜索
            results = collection.search(
                data=[query_embedding],
                anns_field="embedding",
                param=MilvusConfig.SEARCH_PARAMS,
                limit=request.limit * 3,  # 增加搜索量以便在去重后仍有足够结果
                offset=request.offset,
                output_fields=["book_id", "text", "metadata"]
            )
            
            # 处理结果
            hits = []
            for hits_i in results:
                for hit in hits_i:
                    score = float(hit.score)
                    # 使用更宽松的相似度计算
                    similarity_score = 1.0 / (1.0 + score)
                    if similarity_score < MilvusConfig.SIMILARITY_THRESHOLD:
                        continue
                    
                    # 获取book_id并检查是否已处理过
                    book_id = hit.entity.get('book_id')
                    if book_id is None or book_id in processed_book_ids:
                        continue
                    
                    # 记录已处理的book_id
                    processed_book_ids.add(book_id)
                    
                    hits.append({
                        "id": hit.id,
                        "score": similarity_score,
                        "book_id": book_id,
                        "text": hit.entity.get('text'),
                        "metadata": hit.entity.get('metadata')
                    })
            
            # 按分数排序
            hits.sort(key=lambda x: x["score"], reverse=True)
            hits = hits[:request.limit]
            
            if hits:
                logger.info(f"从Milvus找到 {len(hits)} 个结果，去重后剩余 {len(hits)} 本书籍")
                return {"hits": hits}
        except Exception as e:
            logger.warning(f"Milvus搜索失败: {e}")
        
        # 如果Milvus搜索失败，从持久化存储中搜索
        logger.info("尝试从持久化存储中搜索")
        persistent_hits = search_from_persistent_storage(query_embedding, request.limit * 3)  # 增加搜索量
        
        # 去重处理
        hits = []
        for hit in persistent_hits:
            book_id = hit.get("book_id")
            if book_id is None or book_id in processed_book_ids:
                continue
            
            processed_book_ids.add(book_id)
            hits.append(hit)
        
        # 截取所需数量
        hits = hits[:request.limit]
        
        if hits:
            logger.info(f"从持久化存储找到结果，去重后剩余 {len(hits)} 本书籍")
            return {"hits": hits}
        
        logger.warning("未找到任何结果")
        return {"hits": []}
    except Exception as e:
        logger.error(f"搜索失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# 在应用启动时初始化数据库和加载持久化数据
@app.on_event("startup")
async def startup_event():
    init_db()
    load_from_persistent_storage()
    logger.info("数据库初始化和数据加载完成")

def verify_semantic_relevance(query: str, candidate_text: str) -> float:
    """使用模型验证查询和候选文本的语义相关性"""
    try:
        # 构建验证提示
        prompt = f"""请判断以下两个文本的语义相关性，给出0-1之间的分数：
查询文本：{query}
候选文本：{candidate_text}
请只返回一个0-1之间的数字，不要包含其他文字。"""
        
        # 生成响应
        input_ids = tokenizer([prompt], return_tensors="pt").to(model.device)
        generated_ids = model.generate(
            input_ids.input_ids,
            max_new_tokens=10,
            temperature=0.1,
            do_sample=False
        )
        response = tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
        
        # 提取分数
        try:
            score = float(response.strip())
            return max(0.0, min(1.0, score))  # 确保分数在0-1之间
        except ValueError:
            logger.warning(f"无法解析模型返回的分数: {response}")
            return 0.0
    except Exception as e:
        logger.error(f"语义验证失败: {e}")
        return 0.0

@app.get(APIConfig.get_path("/health"))
async def health_check():
    return {"status": "healthy"}

@app.on_event("shutdown")
async def shutdown_event():
    """应用关闭时清理资源"""
    cleanup_milvus()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()

@app.post(APIConfig.get_path("/analyze_type"))
async def analyze_question_type(request: Request):
    """
    分析问题类型的专用接口，返回固定格式
    """
    json_post_raw = await request.json()
    json_post = json.dumps(json_post_raw)
    json_post_list = json.loads(json_post)
    
    # 获取系统提示词和用户提示
    system_prompt = json_post_list.get('system_prompt', '')
    user_prompt = json_post_list.get('text', '')
    
    # 构建消息列表
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    if user_prompt:
        messages.append({"role": "user", "content": user_prompt})
    
    # 生成对话模板
    input_ids = tokenizer.apply_chat_template(
        messages,
        tokenize=False,
        add_generation_prompt=True
    )
    
    # 生成响应
    model_inputs = tokenizer([input_ids], return_tensors="pt").to(model.device)
    generated_ids = model.generate(
        model_inputs.input_ids,
        max_new_tokens=50,  # 使用较小的长度，因为我们只需要类型
        do_sample=False,    # 关闭随机性，确保结果稳定
        temperature=0.1     # 低温度，减少随机性
    )
    generated_ids = [
        output_ids[len(input_ids):] for input_ids, output_ids in zip(model_inputs.input_ids, generated_ids)
    ]
    response = tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]
    
    # 处理响应，提取问题类型
    think_content, answer_content = split_text(response)
    
    # 清理并大写问题类型
    question_type = answer_content.strip().upper()
    
    # 如果结果包含多个单词，取第一个单词
    if ' ' in question_type:
        question_type = question_type.split()[0]
    
    # 删除可能的标点符号
    question_type = re.sub(r'[^\w]', '', question_type)
    
    # 记录结果
    time = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    log = f"[{time}], user_prompt: \"{user_prompt}\", response: \"{question_type}\""
    logger.info(log)
    
    # 清理资源
    torch_gc()
    
    return {"question_type": question_type}

def ensure_complete_chars(text):
    """确保文本没有截断的多字节字符，避免乱码"""
    # UTF-8编码中，多字节字符的第一个字节以11开头，后续字节以10开头
    # 如果最后一个或几个字节是不完整的多字节字符开头，则移除它们
    try:
        # 先尝试编码解码，检查是否有问题
        text.encode('utf-8').decode('utf-8')
        return text
    except UnicodeError:
        # 如果有问题，逐步截断尾部直到解码成功
        for i in range(1, 5):  # 最多检查4个字节
            if len(text) <= i:
                return ""
            try:
                truncated = text[:-i]
                truncated.encode('utf-8').decode('utf-8')
                return truncated
            except UnicodeError:
                continue
        # 如果仍然失败，返回空字符串避免错误
        return ""

if __name__ == '__main__':
    try:
        uvicorn.run(app, host='0.0.0.0', port=8001, workers=2, timeout_keep_alive=300)
    finally:
        cleanup_milvus()