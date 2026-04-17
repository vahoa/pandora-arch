# Spring AI 集成指南

> 基线：JDK 25 + Spring Boot 4.0.5 + Spring AI 1.0.0（GA）

## 1. Spring AI 概述

Spring AI 是 Spring 生态中用于集成大语言模型（LLM）的抽象框架，提供统一的 API 访问多种 AI 服务。

本指南基于 **Spring AI 1.0.0（正式版）**，通过以下方式管理依赖：

- 父 POM 中统一声明 `spring-ai-bom`
- Spring AI 1.0 已在 Maven Central 正式发布，无需额外添加 milestone 仓库

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

业务模块按需引入 Starter：

```xml
<!-- pandora-infrastructure（作为可选能力） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <optional>true</optional>
</dependency>
```

> **注意**：旧版 artifactId `spring-ai-openai-spring-boot-starter` 已改名为 `spring-ai-starter-model-openai`。

---

## 2. 架构设计

采用 **DDD（领域驱动设计）** 分层架构，将 AI 能力与应用业务解耦。

### 2.1 分层结构

| 层级 | 组件 | 职责 |
|------|------|------|
| 应用层 | `AiService` 接口 | 定义 chat、summarize、translate 等业务能力 |
| 基础设施层 | `AiChatService` | 基于 `ChatClient` 实现真实 AI 调用 |
| 基础设施层 | `AiServiceFallback` | 未配置 API 时的兜底实现，返回友好提示 |
| 接口层 | `AiController` | 提供 REST API，对外暴露 AI 能力 |

### 2.2 核心接口定义

```java
public interface AiService {
    String chat(String message);
    String chatWithSystem(String systemPrompt, String userMessage);
    String summarize(String text);
    String translate(String text, String targetLanguage);
}
```

### 2.3 实现关系

- **AiChatService**：实现 `AiService`，内部使用 Spring AI 的 `ChatClient` 调用 OpenAI 或兼容 API
- **AiServiceFallback**：当未配置 `OPENAI_API_KEY` 或配置无效时，作为降级实现，避免应用启动失败

---

## 3. 配置方法

### 3.1 application.yml 配置

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:sk-your-key-here}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
          temperature: 0.7
          max-tokens: 2048
```

### 3.2 环境变量说明

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `OPENAI_API_KEY` | OpenAI API 密钥 | `sk-your-key-here` |
| `OPENAI_BASE_URL` | API 基础地址，可替换为国内代理或兼容 API | `https://api.openai.com` |
| `OPENAI_MODEL` | 使用的模型名称 | `gpt-4o-mini` |

### 3.3 国内代理示例

若使用国内代理或兼容 OpenAI 格式的 API，可设置：

```bash
export OPENAI_BASE_URL=https://your-proxy.example.com/v1
export OPENAI_API_KEY=your-api-key
```

---

## 4. API 接口说明

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| AI 对话 | POST | `/api/ai/chat` | 发送消息进行对话 |
| 带系统提示对话 | POST | `/api/ai/chat/with-system` | 自定义系统角色 |
| 文本摘要 | POST | `/api/ai/summarize` | AI 生成文本摘要 |
| 文本翻译 | POST | `/api/ai/translate` | AI 翻译文本 |

### 4.1 请求/响应格式

- **AI 对话**：请求体 `{"message": "你好"}`，响应为 AI 回复文本
- **带系统提示对话**：请求体 `{"systemPrompt": "你是一个助手", "userMessage": "你好"}`
- **文本摘要**：请求体 `{"text": "待摘要的长文本"}`
- **文本翻译**：请求体 `{"text": "待翻译文本", "targetLanguage": "英文"}`

---

## 5. 使用示例

### 5.1 AI 对话

```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "请用一句话介绍 Spring AI"}'
```

### 5.2 带系统提示对话

```bash
curl -X POST http://localhost:8080/api/ai/chat/with-system \
  -H "Content-Type: application/json" \
  -d '{"systemPrompt": "你是一个专业的 Java 技术顾问", "userMessage": "Spring Boot 3 有哪些新特性？"}'
```

### 5.3 文本摘要

```bash
curl -X POST http://localhost:8080/api/ai/summarize \
  -H "Content-Type: application/json" \
  -d '{"text": "这是一段很长的文本内容，需要 AI 生成简洁的摘要..."}'
```

### 5.4 文本翻译

```bash
curl -X POST http://localhost:8080/api/ai/translate \
  -H "Content-Type: application/json" \
  -d '{"text": "你好，世界", "targetLanguage": "英文"}'
```

---

## 6. 扩展指南

### 6.1 切换为 Ollama（本地模型）

1. 添加 Ollama 依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

2. 配置 application.yml：

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama2
```

3. 注入 `ChatClient` 时指定 `ollama` 作为 provider，或通过 `@Qualifier` 区分不同实现。

### 6.2 添加 RAG 检索增强

1. 引入 `spring-ai-vector-store-*` 和 `spring-ai-embedding-*` 依赖
2. 配置向量存储（如 Redis、PgVector）和 Embedding 模型
3. 使用 `VectorStore` 存储文档向量，`RetrievalAugmentation` 在对话前检索相关文档并注入上下文

```java
// 伪代码示例
@Bean
public RetrievalAugmentationClient retrievalClient(VectorStore vectorStore, EmbeddingModel embeddingModel) {
    return new RetrievalAugmentationClient(vectorStore, embeddingModel);
}
```

### 6.3 自定义 ChatClient 配置

通过 `ChatClient.Builder` 自定义：

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultOptions(OpenAiChatOptions.builder()
            .model("gpt-4o-mini")
            .temperature(0.7)
            .maxTokens(2048)
            .build())
        .build();
}
```

> 说明：Spring AI 1.0 GA 已移除 `ChatOptionsBuilder.builder().withXxx()` 的旧 API，统一使用各模型的 `XxxChatOptions.builder().xxx()` 链式方法。

---

*文档版本：基于 Spring AI 1.0.0，更新日期 2026-04*

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
