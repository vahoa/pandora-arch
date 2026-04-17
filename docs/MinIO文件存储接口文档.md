# MinIO 文件存储接口文档

> 基线：JDK 25 + Spring Boot 4.0.5 + MinIO Java SDK 8.5.17

## 1. MinIO 概述

MinIO 是一款高性能的对象存储服务，兼容 Amazon S3 API，适用于私有云和边缘部署场景。

本系统基于 **MinIO Java SDK 8.5.17** 版本，默认配置如下：

| 配置项 | 值 |
|--------|-----|
| Endpoint | `http://127.0.0.1:9000` |
| Bucket | `corp-web` |
| 控制台 | `http://127.0.0.1:9001`（MinIO Console） |

---

## 2. 架构设计

采用分层架构，将文件存储能力与应用业务解耦。

### 2.1 分层结构

| 层级 | 组件 | 职责 |
|------|------|------|
| 应用层 | `FileService` 接口 | 定义上传、下载、删除、列表等文件操作能力 |
| 基础设施层 | `MinioService` | 封装 MinIO 原生 SDK，提供底层对象操作 |
| 基础设施层 | `MinioFileService` | 实现 `FileService`，组合 `MinioService` 完成业务逻辑 |
| 接口层 | `FileController` | 提供 REST API，对外暴露文件存储能力 |

### 2.2 依赖关系

```
FileController → FileService
MinioFileService implements FileService
MinioFileService → MinioService
```

---

## 3. 配置方法

### 3.1 application.yml 配置

```yaml
pandora:
  minio:
    endpoint: http://127.0.0.1:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: corp-web
```

### 3.2 配置项说明

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `pandora.minio.endpoint` | MinIO 服务地址 | `http://127.0.0.1:9000` |
| `pandora.minio.access-key` | 访问密钥 | `minioadmin` |
| `pandora.minio.secret-key` | 私密密钥 | `minioadmin` |
| `pandora.minio.bucket` | 默认存储桶名称 | `corp-web` |

---

## 4. REST API 接口完整文档

| 接口 | 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| 上传文件 | POST | `/api/files/upload` | file (multipart) | 上传到默认桶 |
| 上传到指定桶 | POST | `/api/files/upload/{bucketName}` | file (multipart) | 上传到指定桶 |
| 下载文件 | GET | `/api/files/download` | objectName | 下载默认桶中的文件 |
| 下载指定桶文件 | GET | `/api/files/download/{bucketName}` | objectName | 下载指定桶中的文件 |
| 获取预签名URL | GET | `/api/files/presigned-url` | objectName | 获取临时访问URL |
| 删除文件 | DELETE | `/api/files` | objectName | 删除默认桶中的文件 |
| 删除指定桶文件 | DELETE | `/api/files/{bucketName}` | objectName | 删除指定桶中的文件 |
| 列出文件 | GET | `/api/files/list` | prefix | 按前缀列出文件 |
| 检查文件存在 | GET | `/api/files/exists` | objectName | 检查文件是否存在 |

---

## 5. curl 调用示例

### 5.1 上传文件（默认桶）

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@/path/to/your/file.pdf"
```

### 5.2 上传到指定桶

```bash
curl -X POST http://localhost:8080/api/files/upload/my-bucket \
  -F "file=@/path/to/your/image.png"
```

### 5.3 下载文件（默认桶）

```bash
curl -X GET "http://localhost:8080/api/files/download?objectName=2024/01/15/uuid-filename.pdf" \
  -o downloaded.pdf
```

### 5.4 下载指定桶文件

```bash
curl -X GET "http://localhost:8080/api/files/download/my-bucket?objectName=2024/01/15/uuid-filename.pdf" \
  -o downloaded.pdf
```

### 5.5 获取预签名 URL

```bash
curl -X GET "http://localhost:8080/api/files/presigned-url?objectName=2024/01/15/uuid-filename.pdf"
```

### 5.6 删除文件（默认桶）

```bash
curl -X DELETE "http://localhost:8080/api/files?objectName=2024/01/15/uuid-filename.pdf"
```

### 5.7 删除指定桶文件

```bash
curl -X DELETE "http://localhost:8080/api/files/my-bucket?objectName=2024/01/15/uuid-filename.pdf"
```

### 5.8 列出文件

```bash
curl -X GET "http://localhost:8080/api/files/list?prefix=2024/01/"
```

### 5.9 检查文件存在

```bash
curl -X GET "http://localhost:8080/api/files/exists?objectName=2024/01/15/uuid-filename.pdf"
```

---

## 6. 底层 MinioService / FileService 直接使用

在业务代码中可直接注入 `FileService` 或 `MinioService` 进行文件操作。

### 6.1 使用 FileService（推荐）

```java
@Service
public class DocumentService {

    private final FileService fileService;

    public DocumentService(FileService fileService) {
        this.fileService = fileService;
    }

    public String saveDocument(MultipartFile file) throws IOException {
        // 上传到默认桶，返回 objectName
        return fileService.upload(file.getInputStream(), file.getOriginalFilename(), file.getContentType());
    }

    public InputStream downloadDocument(String objectName) {
        return fileService.download(objectName);
    }

    public boolean documentExists(String objectName) {
        return fileService.exists(objectName);
    }
}
```

### 6.2 使用 MinioService（底层操作）

```java
@Service
public class CustomStorageService {

    private final MinioService minioService;

    public CustomStorageService(MinioService minioService) {
        this.minioService = minioService;
    }

    public void putObject(String bucket, String objectName, InputStream inputStream, String contentType) {
        minioService.putObject(bucket, objectName, inputStream, contentType);
    }

    public InputStream getObject(String bucket, String objectName) {
        return minioService.getObject(bucket, objectName);
    }

    public String getPresignedUrl(String bucket, String objectName, int expireMinutes) {
        return minioService.getPresignedUrl(bucket, objectName, expireMinutes);
    }
}
```

### 6.3 上传到指定桶

```java
String objectName = fileService.upload("my-bucket", file.getInputStream(), 
    file.getOriginalFilename(), file.getContentType());
```

---

## 7. 注意事项

### 7.1 文件命名策略

系统采用 **日期目录 + UUID** 的命名策略，避免重名和目录过深：

- 格式：`yyyy/MM/dd/{uuid}-{原始文件名}`
- 示例：`2024/01/15/a1b2c3d4-e5f6-7890-abcd-ef1234567890-report.pdf`

### 7.2 大文件上传限制

需在 `application.yml` 中配置 Spring 的 multipart 限制：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

根据业务需求调整 `max-file-size` 和 `max-request-size`。

### 7.3 预签名 URL 有效期

- 预签名 URL 默认有效期为 **7 天**（可配置）
- 过期后需重新获取
- 适用于临时分享、前端直传等场景

### 7.4 安全建议

- 生产环境务必修改默认 `access-key` 和 `secret-key`
- 建议通过环境变量或配置中心注入敏感信息
- 按业务划分不同 bucket，控制访问权限

---

*文档版本：基于 MinIO Java SDK 8.5.17，更新日期 2026-04*

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
