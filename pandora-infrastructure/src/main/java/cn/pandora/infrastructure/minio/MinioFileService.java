package cn.pandora.infrastructure.minio;

import cn.pandora.application.file.FileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * MinIO 文件服务实现（实现应用层 FileService 接口）
 */
@Service
public class MinioFileService implements FileService {

    private final MinioService minioService;

    public MinioFileService(MinioService minioService) {
        this.minioService = minioService;
    }

    @Override
    public String upload(MultipartFile file) {
        return minioService.upload(file);
    }

    @Override
    public String upload(String bucketName, MultipartFile file) {
        return minioService.upload(bucketName, file);
    }

    @Override
    public String upload(String objectName, InputStream inputStream, long size, String contentType) {
        return minioService.upload(objectName, inputStream, size, contentType);
    }

    @Override
    public InputStream download(String objectName) {
        return minioService.getObject(objectName);
    }

    @Override
    public InputStream download(String bucketName, String objectName) {
        return minioService.getObject(bucketName, objectName);
    }

    @Override
    public String getPresignedUrl(String objectName) {
        return minioService.getPresignedUrl(objectName);
    }

    @Override
    public void delete(String objectName) {
        minioService.removeObject(objectName);
    }

    @Override
    public void delete(String bucketName, String objectName) {
        minioService.removeObject(bucketName, objectName);
    }

    @Override
    public List<String> list(String prefix) {
        return minioService.listObjects(prefix);
    }

    @Override
    public boolean exists(String objectName) {
        return minioService.objectExists(objectName);
    }
}
