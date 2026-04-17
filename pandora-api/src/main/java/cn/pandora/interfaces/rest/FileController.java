package cn.pandora.interfaces.rest;

import cn.pandora.application.file.FileService;
import cn.pandora.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理 REST 控制器 —— 暴露 MinIO 全部操作接口
 */
@Tag(name = "文件管理", description = "基于 MinIO 的文件上传、下载、删除、查询等接口")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传文件", description = "上传文件到默认桶，返回对象名称")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file) {
        String objectName = fileService.upload(file);
        return Result.success("文件上传成功", objectName);
    }

    @Operation(summary = "上传文件到指定桶", description = "上传文件到指定桶，返回对象名称")
    @PostMapping(value = "/upload/{bucketName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadToBucket(
            @Parameter(description = "桶名称") @PathVariable String bucketName,
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file) {
        String objectName = fileService.upload(bucketName, file);
        return Result.success("文件上传成功", objectName);
    }

    @Operation(summary = "下载文件", description = "下载默认桶中的文件")
    @GetMapping("/download")
    public void download(
            @Parameter(description = "对象名称") @RequestParam String objectName,
            HttpServletResponse response) {
        streamFile(objectName, response, () -> fileService.download(objectName));
    }

    @Operation(summary = "下载指定桶中的文件")
    @GetMapping("/download/{bucketName}")
    public void downloadFromBucket(
            @Parameter(description = "桶名称") @PathVariable String bucketName,
            @Parameter(description = "对象名称") @RequestParam String objectName,
            HttpServletResponse response) {
        streamFile(objectName, response, () -> fileService.download(bucketName, objectName));
    }

    @Operation(summary = "获取预签名URL", description = "获取文件的临时访问URL（有效期7天）")
    @GetMapping("/presigned-url")
    public Result<String> getPresignedUrl(
            @Parameter(description = "对象名称") @RequestParam String objectName) {
        String url = fileService.getPresignedUrl(objectName);
        return Result.success(url);
    }

    @Operation(summary = "删除文件", description = "删除默认桶中的文件")
    @DeleteMapping
    public Result<Void> delete(
            @Parameter(description = "对象名称") @RequestParam String objectName) {
        fileService.delete(objectName);
        return Result.success();
    }

    @Operation(summary = "删除指定桶中的文件")
    @DeleteMapping("/{bucketName}")
    public Result<Void> deleteFromBucket(
            @Parameter(description = "桶名称") @PathVariable String bucketName,
            @Parameter(description = "对象名称") @RequestParam String objectName) {
        fileService.delete(bucketName, objectName);
        return Result.success();
    }

    @Operation(summary = "列出文件", description = "按前缀列出默认桶中的文件")
    @GetMapping("/list")
    public Result<List<String>> list(
            @Parameter(description = "文件名前缀（可选）") @RequestParam(defaultValue = "") String prefix) {
        List<String> objects = fileService.list(prefix);
        return Result.success(objects);
    }

    @Operation(summary = "检查文件是否存在")
    @GetMapping("/exists")
    public Result<Boolean> exists(
            @Parameter(description = "对象名称") @RequestParam String objectName) {
        boolean exists = fileService.exists(objectName);
        return Result.success(exists);
    }

    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream get();
    }

    private void streamFile(String objectName, HttpServletResponse response, InputStreamSupplier supplier) {
        try {
            String filename = objectName.contains("/")
                    ? objectName.substring(objectName.lastIndexOf("/") + 1)
                    : objectName;
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");

            try (InputStream is = supplier.get(); OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }
}
