import os

# 项目结构
project_structure = {
    "ALLIBERARY": {
        "backend": {
            "src": {
                "main": {
                    "java": {
                        "com": {
                            "allibrary": {
                                "config": [],
                                "controller": [],
                                "service": [],
                                "repository": [],
                                "model": [],
                                "Application.java": None
                            }
                        }
                    },
                    "resources": {
                        "static": [],
                        "templates": [],
                        "application.yml": None
                    }
                }
            },
            "pom.xml": None,
            "Dockerfile": None
        },
        "frontend": {
            "public": {
                "index.html": None,
                "favicon.ico": None
            },
            "src": {
                "assets": [],
                "components": [],
                "pages": [],
                "services": [],
                "App.jsx": None,
                "index.jsx": None
            },
            "package.json": None,
            "Dockerfile": None
        },
        "ai": {
            "data": {
                "raw": [],
                "processed": [],
                "embeddings": []
            },
            "models": {
                "base": {
                    "bert-base-chinese": None
                },
                "fine_tuned": []
            },
            "notebooks": [],
            "scripts": {
                "train.py": None,
                "inference.py": None
            },
            "requirements.txt": None
        },
        "infrastructure": {
            "docker-compose.yml": None,
            "nginx": {
                "nginx.conf": None
            },
            "mysql": {
                "init.sql": None
            }
        },
        "docs": {
            "API.md": None,
            "ARCHITECTURE.md": None
        },
        ".gitignore": None,
        "README.md": None,
        "Makefile": None
    }
}

# 生成文件和文件夹的函数
def create_project_structure(base_path, structure):
    for key, value in structure.items():
        path = os.path.join(base_path, key)
        if isinstance(value, list):
            # 如果是空列表，表示文件
            if value is None:
                open(path, 'w').close()
            else:
                # 如果是非空列表，创建文件夹
                os.makedirs(path, exist_ok=True)
        elif isinstance(value, dict):
            # 如果是字典，递归创建文件夹和文件
            os.makedirs(path, exist_ok=True)
            create_project_structure(path, value)

# 设置项目的根路径
root_path = "ALLIBERARY"
os.makedirs(root_path, exist_ok=True)

# 开始创建项目目录结构
create_project_structure(root_path, project_structure["ALLIBERARY"])

print(f"项目目录结构 {root_path} 已经成功创建！")