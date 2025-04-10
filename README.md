# 图书馆AI助手系统

![版本](https://img.shields.io/badge/版本-1.0.0-blue)
![前端](https://img.shields.io/badge/前端-Vue3-brightgreen)
![后端](https://img.shields.io/badge/后端-SpringBoot-orange)
![AI](https://img.shields.io/badge/AI-DeepSeek-blueviolet)

## 目录

- [项目概述](#项目概述)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [模块说明](#模块说明)
  - [AI模块](#ai模块)
  - [后端模块](#后端模块)
  - [前端模块](#前端模块)
- [环境配置](#环境配置)
- [部署指南](#部署指南)
- [端口配置](#端口配置)
- [FAQ](#faq)
- [联系方式](#联系方式)

## 项目概述

本项目是基于大型语言模型技术的智能图书馆助手系统，旨在为图书馆用户提供智能化的服务体验。系统整合了图书推荐、知识问答、规则咨询、资源预约等功能，通过自然语言交互方式为用户提供便捷高效的图书馆服务。

### 核心功能

- **智能图书推荐**：基于用户兴趣和阅读历史进行个性化图书推荐
- **图书馆规则咨询**：回答有关借阅规则、积分规则、违规处理的问题
- **知识问答**：针对特定领域知识提供准确回答
- **资源预约**：提供座位、储物柜等资源的预约咨询

## 系统架构

![系统架构图](https://via.placeholder.com/800x400?text=Library+AI+System+Architecture)

系统采用前后端分离的微服务架构，分为三大核心模块：

1. **AI服务模块**：负责自然语言处理、模型推理和知识处理
2. **后端业务模块**：处理业务逻辑、数据存储和API服务
3. **前端交互模块**：提供用户界面和交互体验

### 数据流向

用户输入内容请求 → 前端处理 → 后端API → FastAPI服务 → AI模型推理 → 数据库查询&内容增强 → 返回前端 → 内容渲染

## 技术栈

### AI模块
- **核心框架**：FastAPI, Transformers, PyTorch
- **模型**：DeepSeek-V3, DeepSeek-r1-Distill-Llama-8B (微调版)
- **向量数据库**：Milvus
- **其他工具**：Huggingface-Hub, Scikit-learn

### 后端模块
- **核心框架**：Spring Boot 3.4.3, Spring Security
- **数据库**：MySQL
- **API文档**：Swagger
- **工具库**：Lombok, JWT, MyBatis
- **通信**：Spring WebFlux (响应式编程)

### 前端模块
- **核心框架**：Vue 3, Vite
- **状态管理**：Pinia
- **UI组件**：Naive UI
- **CSS工具**：UnoCSS, SCSS
- **HTTP客户端**：Axios
- **Markdown渲染**：Markdown-it, Highlight.js

## 模块说明

### AI模块

AI模块是整个系统的核心，负责自然语言处理和模型推理，为用户提供智能化的回答和推荐。

#### 技术实现

- **模型微调**：使用QLoRA技术对DeepSeek-r1-Distill-Llama-8B模型进行微调
- **流式输出**：基于SSE (Server-Sent Events)实现流式文本生成
- **文本向量化**：使用嵌入模型将文本转化为向量进行语义检索
- **知识库管理**：结构化存储图书馆领域知识，支持语义检索

#### 关键目录结构
```
ai/
├── data/              # 数据处理相关
│   ├── embeddings/    # 嵌入向量模型
│   ├── tools/         # 数据处理工具
│   ├── processed/     # 处理后的数据
│   └── raw/           # 原始数据
├── models/            # 模型定义
│   ├── app/           # FastAPI应用
│   └── base/          # 基础模型接口
└── LiberaryData/      # 图书馆领域数据
```

#### 配置说明

AI服务配置主要通过`.env`文件和环境变量进行设置，关键配置包括：

- **模型路径**：配置本地模型或远程API的路径
- **向量数据库**：Milvus连接配置
- **服务参数**：批处理大小、最大长度、温度等推理参数

### 后端模块

后端模块基于Spring Boot框架开发，提供REST API接口，负责业务逻辑处理、用户认证、数据存储等功能。

#### 技术实现

- **响应式编程**：使用Spring WebFlux支持高并发请求处理
- **安全认证**：基于JWT的用户认证和授权
- **ORM映射**：使用MyBatis进行对象关系映射
- **API网关**：处理跨域请求、请求转发和负载均衡

#### 关键目录结构
```
backend/
├── backend-ai/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/org/example/backendai/
│   │   │   │   ├── config/           # 配置类
│   │   │   │   ├── controller/       # 控制器
│   │   │   │   ├── DTO/              # 数据传输对象
│   │   │   │   ├── entity/           # 实体类
│   │   │   │   ├── mapper/           # MyBatis映射
│   │   │   │   ├── service/          # 服务层
│   │   │   │   └── util/             # 工具类
│   │   │   └── resources/
│   │   │       ├── application.properties  # 应用配置
│   │   │       └── db/                    # SQL脚本
│   │   └── test/                   # 测试代码
│   └── pom.xml                    # Maven配置
└── setup-scripts/                # 环境配置脚本
```

#### 配置说明

后端服务配置主要在`application.properties`文件中定义：

- **数据库配置**：数据库连接信息
  ```properties
  spring.datasource.url=jdbc:mysql://localhost:3306/book_db
  spring.datasource.username=root
  spring.datasource.password=5233
  ```
- **AI服务配置**：AI模型服务的基础URL
  ```properties
  custom-model.api-url=http://localhost:8000/generate
  custom-model.api-url-base=http://localhost:8000
  ```
- **JWT配置**：JWT密钥和过期时间
  ```properties
  jwt.secret=E8zxlUd0UJUpQQUUdwPbTJvP2XlLkbUZmTDgE1PbqXxdJcwPk7IlLQn3AcdTqcqLCRDvQZ6yz1UgJD3ZdbwRRR4ZfdjqpEOiJJt2
  jwt.expiration=86400000  # 24小时
  ```

### 前端模块

前端模块基于Vue 3开发，提供用户友好的界面和交互体验，支持多种设备适配。

#### 技术实现

- **组件化开发**：基于Vue 3的组件化架构
- **响应式设计**：适配不同设备屏幕尺寸
- **Markdown渲染**：支持富文本和Markdown格式内容展示
- **流式响应**：基于SSE实现打字机效果的流式文本展示
- **状态管理**：使用Pinia进行全局状态管理

#### 关键目录结构
```
frontend/
└── chatbot/
    ├── public/              # 静态资源
    ├── src/
    │   ├── api/             # API接口
    │   ├── assets/          # 图片、字体等资源
    │   ├── components/      # 通用组件
    │   ├── router/          # 路由配置
    │   ├── store/           # 状态管理
    │   ├── styles/          # 全局样式
    │   ├── utils/           # 工具函数
    │   └── views/           # 页面视图
    ├── index.html           # HTML入口
    ├── package.json         # 依赖配置
    └── vite.config.ts       # Vite配置
```

#### 配置说明

前端配置主要通过`.env`文件和`vite.config.ts`进行设置：

- **API基础URL**：后端API服务地址
  ```
  VITE_API_BASE_URL=http://localhost:8080
  ```
- **构建配置**：打包输出目录和资源引用路径
- **代理配置**：开发环境的API代理设置

## 环境配置

### 系统要求

- **操作系统**：Windows/Linux/macOS
- **AI模块**：Python 3.10+
- **后端模块**：JDK 17, MySQL 8.0+
- **前端模块**：Node.js 18+, pnpm

### AI环境配置

1. 安装Python 3.10+和依赖包
   ```bash
   cd ai
   pip install -r models/app/requirements.txt
   ```

2. 配置模型
   - 使用自己的模型：将模型文件放入相应目录
   - 使用API：在`.env`文件中配置API地址和密钥

### 后端环境配置

1. 安装JDK 17
   ```bash
   # Windows
   .\backend\install-jdk-windows.bat

   # Linux/macOS
   chmod +x ./backend/setup-env.sh
   ./backend/setup-env.sh
   ```

2. 配置MySQL数据库
   ```bash
   # Windows
   .\backend\setup-database.bat

   # Linux/macOS
   mysql -u root -p < ./backend/backend-ai/src/main/resources/db/init-user.sql
   mysql -u root -p < ./backend/backend-ai/src/main/resources/db/init-chat.sql
   mysql -u root -p < ./backend/backend-ai/src/main/resources/db/init-book-recommendation.sql
   mysql -u root -p < ./backend/backend-ai/src/main/resources/db/init-user-profile.sql
   ```

3. 修改数据库连接配置
   - 路径：`backend/backend-ai/src/main/resources/application.properties`

### 前端环境配置

1. 安装Node.js 18+和pnpm
   ```bash
   npm install -g pnpm
   ```

2. 安装依赖
   ```bash
   cd frontend/chatbot
   pnpm install
   ```

3. 配置环境变量
   - 创建`.env.local`文件配置后端API地址

## 部署指南

### AI服务部署

```bash
cd ai/models/app
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 2
```

### 后端服务部署

```bash
# Windows
cd backend
.\start-service.bat

# Linux/macOS
cd backend
chmod +x ./start-service.sh
./start-service.sh
```

### 前端应用部署

#### 开发环境
```bash
cd frontend/chatbot
pnpm dev
```

#### 生产环境
```bash
cd frontend/chatbot
pnpm build
# 部署dist目录到Web服务器
```

## 端口配置

系统各服务的默认端口配置：

| 服务 | 端口 | 说明 |
|------|------|------|
| AI服务 | 8000 | FastAPI服务，提供模型推理接口 |
| 后端服务 | 8080 | Spring Boot应用，提供业务API |
| 前端开发服务 | 5173 | Vite开发服务器 |
| MySQL | 3306 | 数据库服务 |
| Milvus | 19530 | 向量数据库服务（如配置） |

## FAQ

### 常见问题

**Q: 如何更换AI模型？**
A: 在`.env`文件中修改模型路径配置，或使用API接口方式接入其他模型。

**Q: 如何调整模型回复的风格和内容？**
A: 可以修改`ai/models/app/configs/prompts.py`中的提示词模板。

**Q: 数据库连接失败怎么办？**
A: 检查`application.properties`中的数据库配置是否正确，确保MySQL服务正常运行。

**Q: 如何添加新的图书数据？**
A: 可以通过MySQL客户端直接操作数据库，或开发后台管理界面进行数据维护。

## 联系方式

如有问题或建议，请通过以下方式联系我们：
- GitHub Issues: [https://github.com/Gorillaorange/Liberary-Ai/issues](https://github.com/Gorillaorange/Liberary-Ai/issues)
- Email: [your-email@example.com](mailto:your-email@example.com)

## 许可证

[MIT License](LICENSE)

---
最后更新: 2023年10月31日


