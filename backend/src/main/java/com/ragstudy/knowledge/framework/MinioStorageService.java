package com.ragstudy.knowledge.framework;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioStorageService(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public String upload(String objectName, MultipartFile file) {
        ensureConfigured();
        ensureBucket();

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .contentType(resolveContentType(file))
                            .stream(inputStream, file.getSize(), -1)
                            .build()
            );
            return properties.getBucket() + "/" + objectName;
        } catch (Exception exception) {
            throw new IllegalStateException("文件上传到 MinIO 失败", exception);
        }
    }

    public String upload(String objectName, byte[] content, String contentType) {
        ensureConfigured();
        ensureBucket();

        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                            .stream(inputStream, content.length, -1)
                            .build()
            );
            return properties.getBucket() + "/" + objectName;
        } catch (Exception exception) {
            throw new IllegalStateException("文件上传到 MinIO 失败", exception);
        }
    }

    public GetObjectResponse open(String objectName) {
        ensureConfigured();

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("读取 MinIO 文件失败", exception);
        }
    }

    public void delete(String objectName) {
        ensureConfigured();

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("删除 MinIO 文件失败", exception);
        }
    }

    public String bucketName() {
        ensureConfigured();
        return properties.getBucket();
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("MinIO bucket 未配置");
        }

        if (!StringUtils.hasText(properties.getAccessKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("MinIO access key 或 secret key 未配置");
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());

            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO bucket 检查失败", exception);
        }
    }

    private String resolveContentType(MultipartFile file) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }

        return "application/octet-stream";
    }
}
