package cn.pandora.infrastructure.minio;

import cn.pandora.infrastructure.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件操作服务
 */
@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public MinioService(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    /**
     * 确保桶存在，不存在则创建
     */
    public void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建MinIO桶: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("检查/创建桶失败: {}", e.getMessage(), e);
            throw new RuntimeException("MinIO桶操作失败", e);
        }
    }

    /**
     * 上传文件，返回对象名称
     */
    public String upload(MultipartFile file) {
        return upload(minioConfig.getBucket(), file);
    }

    /**
     * 上传文件到指定桶
     */
    public String upload(String bucketName, MultipartFile file) {
        ensureBucketExists(bucketName);
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectName = generateObjectName(extension);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("文件上传成功: bucket={}, object={}", bucketName, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 上传流数据
     */
    public String upload(String objectName, InputStream inputStream,
                         long size, String contentType) {
        return upload(minioConfig.getBucket(), objectName, inputStream, size, contentType);
    }

    public String upload(String bucketName, String objectName, InputStream inputStream,
                         long size, String contentType) {
        ensureBucketExists(bucketName);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            return objectName;
        } catch (Exception e) {
            log.error("流上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 获取文件流
     */
    public InputStream getObject(String objectName) {
        return getObject(minioConfig.getBucket(), objectName);
    }

    public InputStream getObject(String bucketName, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("获取文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件获取失败", e);
        }
    }

    /**
     * 获取预签名 URL（默认有效期 7 天）
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(minioConfig.getBucket(), objectName, 7, TimeUnit.DAYS);
    }

    public String getPresignedUrl(String bucketName, String objectName,
                                  int duration, TimeUnit unit) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(duration, unit)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名URL失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成预签名URL失败", e);
        }
    }

    /**
     * 删除文件
     */
    public void removeObject(String objectName) {
        removeObject(minioConfig.getBucket(), objectName);
    }

    public void removeObject(String bucketName, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            log.info("文件删除成功: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

    /**
     * 列出桶中的文件
     */
    public List<String> listObjects(String prefix) {
        return listObjects(minioConfig.getBucket(), prefix);
    }

    public List<String> listObjects(String bucketName, String prefix) {
        List<String> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build());
            for (Result<Item> result : results) {
                objects.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.error("列出文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("列出文件失败", e);
        }
        return objects;
    }

    /**
     * 检查文件是否存在
     */
    public boolean objectExists(String objectName) {
        return objectExists(minioConfig.getBucket(), objectName);
    }

    public boolean objectExists(String bucketName, String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String generateObjectName(String extension) {
        return java.time.LocalDate.now().toString().replace("-", "/")
                + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
    }
}
