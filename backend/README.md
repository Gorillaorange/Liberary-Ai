# Java后端开发环境

本项目是一个基于Spring Boot的Java后端应用，使用Maven作为构建工具。

## 开发环境要求

- JDK 17 或更高版本
- Maven 3.8+ (内置了Maven Wrapper，可不单独安装)
- MySQL 数据库 (需要创建book_db数据库)

## 如何启动服务

### Windows
```
start-service.bat
```

### Linux/Mac
```
chmod +x start-service.sh
./start-service.sh
```

## 项目结构

- `backend-ai` - 主项目目录
  - `src/main/java` - Java源代码
  - `src/main/resources` - 配置文件
  - `pom.xml` - Maven依赖管理

## 配置数据库

在`src/main/resources/application.properties`中修改数据库配置：

```
spring.datasource.url=jdbc:mysql://localhost:3306/book_db
spring.datasource.username=root
spring.datasource.password=5233
```

## 开发指南

1. 使用IntelliJ IDEA打开项目
2. 确保已经安装JDK 17
3. 第一次打开项目时，Maven会自动下载依赖，请等待完成
4. 主启动类为`org.example.backendai.BackendAiApplication` 