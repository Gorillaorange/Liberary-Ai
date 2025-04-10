from pathlib import Path

class APIConfig:
    PREFIX = "aiforward895686727887224832"
    GENERATE = "/generate"
    EMBEDDINGS = "/embeddings"
    SEARCH = "/search"
    HEALTH = "/health"
    
    @classmethod
    def get_path(cls, endpoint: str) -> str:
        return f"/{cls.PREFIX}{endpoint}"

class DataConfig:
    DATA_DIR = Path("service/app/db/data_dir")
    EMBEDDINGS_FILE = DATA_DIR / "embeddings.json"
    DB_FILE = DATA_DIR / "embeddings.db"
    
    @classmethod
    def ensure_data_dir(cls):
        cls.DATA_DIR.mkdir(parents=True, exist_ok=True)

class MilvusConfig:
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

class ModelConfig:
    DEVICE = "cuda"
    DEVICE_ID = "0"
    CUDA_DEVICE = f"{DEVICE}:{DEVICE_ID}" if DEVICE_ID else DEVICE
    LOCAL_MODEL_PATH = "/public/home/ssjxhxy/my_models/deepseek-14b"
    MAX_SEQ_LENGTH = 4096
    LOAD_IN_4BIT = True 