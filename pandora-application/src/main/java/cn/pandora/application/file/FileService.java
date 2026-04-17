package cn.pandora.application.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储服务接口（应用层定义，基础设施层实现）
 */
public interface FileService {

    String upload(MultipartFile file);

    String upload(String bucketName, MultipartFile file);

    String upload(String objectName, InputStream inputStream, long size, String contentType);

    InputStream download(String objectName);

    InputStream download(String bucketName, String objectName);

    String getPresignedUrl(String objectName);

    void delete(String objectName);

    void delete(String bucketName, String objectName);

    List<String> list(String prefix);

    boolean exists(String objectName);
}
