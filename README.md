# DDD 架构底座

基于 **Spring Boot 3.2 + OAuth2 + DDD（领域驱动设计）** 的多模块 Maven 项目架构底座。
同时支持 **单体部署** 和 **Spring Cloud Alibaba 微服务部署** 两种模式。

## 模块结构

```
pandora-arch/
├── pandora-common/                    # 公共模块：统一响应、异常体系、基础类
├── pandora-common-cloud/              # 微服务公共模块：Nacos、Sentinel、Feign
├── pandora-domain/                    # 领域层：聚合根、值对象、领域事件、仓储接口
├── pandora-application/               # 应用层：应用服务、Command、DTO
├── pandora-infrastructure/            # 基础设施层：持久化、缓存、安全配置
├── pandora-api/                       # 接口层：REST 控制器、全局异常、Swagger
├── pandora-auth/                      # OAuth2 授权服务器（独立部署）
├── pandora-start/                     # 单体模式启动入口
├── pandora-gateway/                   # API 网关（微服务模式）
└── pandora-service/                   # 微服务启动模块
    └── pandora-service-user/          # 用户微服务
```

## 快速开始

```bash
# 1. 初始化数据库
mysql -u root -p123456 < docs/sql/schema.sql
mysql -u root -p123456 < docs/sql/schema-auth.sql

# 2. 编译项目
mvn clean package -DskipTests

# 3. 启动认证服务器（端口 9100，支持 9 种登录方式）
cd pandora-auth && mvn spring-boot:run

# 4. 启动主应用（端口 8080）
cd pandora-start && mvn spring-boot:run

# 5. 访问 Swagger 文档
# http://localhost:8080/swagger-ui.html
```

详细部署说明请查看 [快速开始文档](docs/getting-started.md)。

## 文档目录

### 核心文档（中文）

| 文档 | 说明 |
|------|------|
| [架构模块详解](docs/架构模块详解.md) | 每个模块的意义、作用、用途、用法、依赖关系及配置说明 |
| [DDD领域驱动设计说明](docs/DDD领域驱动设计说明.md) | DDD 四层架构在本项目中的具体设计与实现 |
| [多数据源与多ORM使用指南](docs/多数据源与多ORM使用指南.md) | 多数据源无缝切换、MyBatis-Plus 与 JPA 多 ORM 共存 |
| [Spring AI集成指南](docs/Spring%20AI集成指南.md) | Spring AI 集成配置、API 接口、扩展指南 |
| [MinIO文件存储接口文档](docs/MinIO文件存储接口文档.md) | MinIO 全部 REST API 接口文档与调用示例 |
| [单体服务部署指南](docs/单体服务部署指南.md) | 单体模式完整配置、启动步骤、认证测试、生产部署 |
| [微服务分布式部署指南](docs/微服务分布式部署指南.md) | 微服务模式 Nacos/Gateway/Sentinel 配置与 Docker 部署 |
| [完整部署操作手册](docs/完整部署操作手册.md) | **从零到启动全部服务**：中间件安装、配置核对、启动顺序、排错指南 |

### 基础文档

| 文档 | 说明 |
|------|------|
| [架构设计说明](docs/architecture.md) | 技术栈、模块职责、架构图、依赖关系、端口规划 |
| [快速开始](docs/getting-started.md) | 环境要求、单体部署、微服务部署、构建命令 |
| [OAuth2 认证授权指南](docs/oauth2-guide.md) | 认证流程、令牌获取、客户端配置、扩展方式 |
| [微服务指南](docs/cloud-guide.md) | Nacos、Sentinel、OpenFeign、Gateway 使用说明 |
| [DDD 分层规范](docs/ddd-specification.md) | 各层规范、代码示例、扩展新限界上下文指南 |
| [数据库脚本](docs/sql/schema.sql) | 数据库初始化 SQL |
