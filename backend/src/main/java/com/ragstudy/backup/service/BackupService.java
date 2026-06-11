package com.ragstudy.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragstudy.backup.controller.dto.BackupConfigDto;
import com.ragstudy.backup.controller.dto.BackupConfigRequest;
import com.ragstudy.backup.controller.dto.BackupItemDto;
import com.ragstudy.backup.controller.dto.BackupResultDto;
import com.ragstudy.backup.dal.dataobject.BackupConfigEntity;
import com.ragstudy.backup.dal.repository.BackupConfigRepository;
import com.ragstudy.knowledge.framework.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class BackupService {

    private static final String CONFIG_ID = "s3";
    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());
    private static final DateTimeFormatter BACKUP_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final BackupConfigRepository backupConfigRepository;
    private final DataSource dataSource;
    private final MinioStorageProperties minioStorageProperties;
    private final ObjectMapper objectMapper;
    private final ReentrantLock backupLock = new ReentrantLock();

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    public BackupService(
            BackupConfigRepository backupConfigRepository,
            DataSource dataSource,
            MinioStorageProperties minioStorageProperties,
            ObjectMapper objectMapper
    ) {
        this.backupConfigRepository = backupConfigRepository;
        this.dataSource = dataSource;
        this.minioStorageProperties = minioStorageProperties;
        this.objectMapper = objectMapper;
    }

    public BackupConfigDto getConfig() {
        return backupConfigRepository.findById(CONFIG_ID)
                .map(this::toDto)
                .orElseGet(() -> new BackupConfigDto(
                        false,
                        "",
                        "",
                        "",
                        "",
                        "backups",
                        "0 2 * * *",
                        14,
                        7,
                        null,
                        null,
                        true,
                        false,
                        "pg_dump",
                        "psql"
                ));
    }

    @Transactional
    public BackupConfigDto saveConfig(BackupConfigRequest request) {
        BackupConfigEntity config = backupConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            BackupConfigEntity created = new BackupConfigEntity();
            created.setId(CONFIG_ID);
            created.setCreatedAt(Instant.now());
            return created;
        });

        config.setEnabled(request.enabled());
        config.setEndpoint(request.endpoint().trim());
        config.setBucket(request.bucket().trim());
        config.setAccessKey(request.accessKey().trim());

        if (StringUtils.hasText(request.secretKey())) {
            config.setSecretKey(request.secretKey().trim());
        } else if (!StringUtils.hasText(config.getSecretKey())) {
            throw new IllegalArgumentException("首次保存 S3 备份配置时必须填写 Secret Key");
        }

        config.setRegion(StringUtils.hasText(request.region()) ? request.region().trim() : "");
        config.setPrefix(normalizePrefix(request.prefix()));
        String cronExpression = normalizeCronExpression(request.cronExpression());
        validateCron(cronExpression);
        config.setSchedule(cronExpression);
        config.setRetentionDays(request.retentionDays());
        config.setRetentionCount(request.retentionCount());
        config.setPathStyleAccess(request.pathStyleAccess());
        config.setPgDumpPath(normalizeCommandPath(request.pgDumpPath(), "pg_dump"));
        config.setPsqlPath(normalizeCommandPath(request.psqlPath(), "psql"));
        config.setUpdatedAt(Instant.now());

        return toDto(backupConfigRepository.save(config));
    }

    public void testConfig(BackupConfigRequest request) {
        validateCron(normalizeCronExpression(request.cronExpression()));
        BackupConfigEntity config = new BackupConfigEntity();
        config.setEndpoint(request.endpoint().trim());
        config.setBucket(request.bucket().trim());
        config.setAccessKey(request.accessKey().trim());
        config.setSecretKey(request.secretKey());
        config.setRegion(StringUtils.hasText(request.region()) ? request.region().trim() : "");
        config.setPathStyleAccess(request.pathStyleAccess());

        if (!StringUtils.hasText(config.getSecretKey())) {
            backupConfigRepository.findById(CONFIG_ID).ifPresent((savedConfig) -> config.setSecretKey(savedConfig.getSecretKey()));
        }

        if (!StringUtils.hasText(config.getSecretKey())) {
            throw new IllegalArgumentException("测试连接需要填写 Secret Key");
        }

        MinioClient client = createClient(config);
        ensureBucket(client, config.getBucket());
        String testObjectName = normalizePrefix(request.prefix()) + "/.connection-test";

        try (InputStream inputStream = InputStream.nullInputStream()) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(config.getBucket())
                            .object(testObjectName)
                            .stream(inputStream, 0, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            client.removeObject(RemoveObjectArgs.builder().bucket(config.getBucket()).object(testObjectName).build());
        } catch (Exception exception) {
            throw new IllegalStateException("S3 测试连接失败", exception);
        }
    }

    public void testDatabaseTools(BackupConfigRequest request) {
        PostgresConnectionInfo connectionInfo = parsePostgresConnection();
        String pgDumpPath = normalizeCommandPath(request.pgDumpPath(), "pg_dump");
        String psqlPath = normalizeCommandPath(request.psqlPath(), "psql");

        try {
            runProcess(List.of(pgDumpPath, "--version"));
            runProcess(List.of(psqlPath, "--version"));
            runProcess(List.of(
                    psqlPath,
                    "-h", connectionInfo.host(),
                    "-p", connectionInfo.port(),
                    "-U", datasourceUsername,
                    "-d", connectionInfo.database(),
                    "-v", "ON_ERROR_STOP=1",
                    "-c", "select 1"
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("数据库备份工具测试失败：" + rootCauseMessage(exception), exception);
        }
    }

    public List<BackupItemDto> listBackups() {
        BackupConfigEntity config = requireConfig();
        MinioClient client = createClient(config);
        ensureBucket(client, config.getBucket());

        List<BackupItemDto> backups = new ArrayList<>();
        String prefix = normalizePrefix(config.getPrefix());

        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(config.getBucket())
                            .prefix(prefix + "/")
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();

                if (!item.objectName().endsWith(".zip")) {
                    continue;
                }

                backups.add(new BackupItemDto(
                        item.objectName(),
                        Path.of(item.objectName()).getFileName().toString(),
                        item.size(),
                        item.lastModified() == null ? null : item.lastModified().toInstant()
                ));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("读取 S3 备份列表失败", exception);
        }

        backups.sort(Comparator.comparing(BackupItemDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return backups;
    }

    public BackupResultDto createBackup(String reason) {
        BackupConfigEntity config = requireConfig();

        if (!backupLock.tryLock()) {
            throw new IllegalStateException("已有备份或恢复任务正在执行");
        }

        Path tempDirectory = null;

        try {
            tempDirectory = Files.createTempDirectory("rag-study-backup-");
            Path databaseDump = tempDirectory.resolve("database.sql");
            Path backupZip = tempDirectory.resolve("backup.zip");
            Instant now = Instant.now();
            String fileName = "rag-study-" + BACKUP_TIME_FORMATTER.format(now) + ".zip";
            String objectName = normalizePrefix(config.getPrefix()) + "/" + fileName;

            dumpDatabase(config, databaseDump);
            writeBackupZip(backupZip, databaseDump, now, reason);

            MinioClient client = createClient(config);
            ensureBucket(client, config.getBucket());

            try (InputStream inputStream = Files.newInputStream(backupZip)) {
                client.putObject(
                        PutObjectArgs.builder()
                                .bucket(config.getBucket())
                                .object(objectName)
                                .contentType("application/zip")
                                .stream(inputStream, Files.size(backupZip), -1)
                                .build()
                );
            }

            config.setLastBackupAt(now);
            config.setUpdatedAt(now);
            backupConfigRepository.save(config);
            cleanupRetention(config, client);

            return new BackupResultDto(objectName, fileName, Files.size(backupZip), now);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "创建 S3 备份失败", exception);
            throw new IllegalStateException("创建 S3 备份失败：" + rootCauseMessage(exception), exception);
        } finally {
            backupLock.unlock();

            if (tempDirectory != null) {
                FileSystemUtils.deleteRecursively(tempDirectory.toFile());
            }
        }
    }

    public void deleteBackup(String objectName) {
        BackupConfigEntity config = requireConfig();
        validateObjectName(config, objectName);

        try {
            createClient(config).removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(config.getBucket())
                            .object(objectName)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("删除 S3 备份失败", exception);
        }
    }

    public void restoreBackup(String objectName) {
        BackupConfigEntity config = requireConfig();
        validateObjectName(config, objectName);

        if (!backupLock.tryLock()) {
            throw new IllegalStateException("已有备份或恢复任务正在执行");
        }

        Path tempDirectory = null;

        try {
            tempDirectory = Files.createTempDirectory("rag-study-restore-");
            Path backupZip = tempDirectory.resolve("backup.zip");
            Path extractDirectory = tempDirectory.resolve("extract");
            Files.createDirectories(extractDirectory);

            MinioClient client = createClient(config);

            try (InputStream inputStream = client.getObject(
                    GetObjectArgs.builder().bucket(config.getBucket()).object(objectName).build()
            )) {
                Files.copy(inputStream, backupZip);
            }

            unzip(backupZip, extractDirectory);
            restoreDatabase(config, extractDirectory.resolve("database.sql"));
            restoreObjects(extractDirectory.resolve("objects"), client);
        } catch (Exception exception) {
            throw new IllegalStateException("恢复备份失败", exception);
        } finally {
            backupLock.unlock();

            if (tempDirectory != null) {
                FileSystemUtils.deleteRecursively(tempDirectory.toFile());
            }
        }
    }

    public boolean shouldRunScheduledBackup(Instant now) {
        Optional<BackupConfigEntity> optionalConfig = backupConfigRepository.findById(CONFIG_ID);

        if (optionalConfig.isEmpty() || !optionalConfig.get().isEnabled()) {
            return false;
        }

        BackupConfigEntity config = optionalConfig.get();
        Instant lastBackupAt = config.getLastBackupAt();

        if (lastBackupAt == null) {
            return true;
        }

        CronExpression cronExpression = CronExpression.parse(config.getSchedule());
        ZonedDateTime lastRun = ZonedDateTime.ofInstant(lastBackupAt, ZoneId.systemDefault());
        ZonedDateTime nextRun = cronExpression.next(lastRun);

        return nextRun != null && !nextRun.toInstant().isAfter(now);
    }

    private void writeBackupZip(Path backupZip, Path databaseDump, Instant createdAt, String reason) throws Exception {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(backupZip))) {
            Map<String, Object> manifest = new HashMap<>();
            manifest.put("app", "rag-study");
            manifest.put("version", "1");
            manifest.put("createdAt", createdAt.toString());
            manifest.put("reason", StringUtils.hasText(reason) ? reason : "manual");
            manifest.put("database", "postgresql");
            manifest.put("objectBucket", minioStorageProperties.getBucket());

            addBytes(zipOutputStream, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            addFile(zipOutputStream, "database.sql", databaseDump);
            addObjectStorageFiles(zipOutputStream);
        }
    }

    private void addObjectStorageFiles(ZipOutputStream zipOutputStream) {
        if (!StringUtils.hasText(minioStorageProperties.getBucket())) {
            return;
        }

        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioStorageProperties.getEndpoint())
                    .credentials(minioStorageProperties.getAccessKey(), minioStorageProperties.getSecretKey())
                    .build();

            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioStorageProperties.getBucket())
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();

                if (item.isDir()) {
                    continue;
                }

                try (InputStream inputStream = client.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioStorageProperties.getBucket())
                                .object(item.objectName())
                                .build()
                )) {
                    zipOutputStream.putNextEntry(new ZipEntry("objects/" + item.objectName()));
                    inputStream.transferTo(zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("打包对象存储文件失败", exception);
        }
    }

    private void restoreObjects(Path objectsDirectory, MinioClient ignoredBackupClient) throws Exception {
        if (!Files.exists(objectsDirectory) || !StringUtils.hasText(minioStorageProperties.getBucket())) {
            return;
        }

        MinioClient client = MinioClient.builder()
                .endpoint(minioStorageProperties.getEndpoint())
                .credentials(minioStorageProperties.getAccessKey(), minioStorageProperties.getSecretKey())
                .build();
        ensureBucket(client, minioStorageProperties.getBucket());
        deleteCurrentObjectStorageFiles(client);

        try (var paths = Files.walk(objectsDirectory)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                String objectName = objectsDirectory.relativize(file).toString().replace("\\", "/");

                try (InputStream inputStream = Files.newInputStream(file)) {
                    client.putObject(
                            PutObjectArgs.builder()
                                    .bucket(minioStorageProperties.getBucket())
                                    .object(objectName)
                                    .stream(inputStream, Files.size(file), -1)
                                    .build()
                    );
                }
            }
        }
    }

    private void deleteCurrentObjectStorageFiles(MinioClient client) {
        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioStorageProperties.getBucket())
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();

                if (item.isDir()) {
                    continue;
                }

                client.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioStorageProperties.getBucket())
                                .object(item.objectName())
                                .build()
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException("清空当前对象存储文件失败", exception);
        }
    }

    private void dumpDatabase(BackupConfigEntity config, Path outputFile) throws Exception {
        PostgresConnectionInfo connectionInfo = parsePostgresConnection();
        List<String> command = List.of(
                normalizeCommandPath(config.getPgDumpPath(), "pg_dump"),
                "-h", connectionInfo.host(),
                "-p", connectionInfo.port(),
                "-U", datasourceUsername,
                "-d", connectionInfo.database(),
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "-f", outputFile.toString()
        );

        runProcess(command);
    }

    private void restoreDatabase(BackupConfigEntity config, Path dumpFile) throws Exception {
        if (!Files.exists(dumpFile)) {
            throw new IllegalStateException("备份中缺少 database.sql");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("select 1");
        }

        PostgresConnectionInfo connectionInfo = parsePostgresConnection();
        List<String> command = List.of(
                normalizeCommandPath(config.getPsqlPath(), "psql"),
                "-h", connectionInfo.host(),
                "-p", connectionInfo.port(),
                "-U", datasourceUsername,
                "-d", connectionInfo.database(),
                "-v", "ON_ERROR_STOP=1",
                "-f", dumpFile.toString()
        );

        runProcess(command);
    }

    private void runProcess(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PGPASSWORD", datasourcePassword);
        processBuilder.redirectErrorStream(true);
        Process process;

        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            String executable = command.isEmpty() ? "命令" : command.get(0);
            throw new IllegalStateException("无法启动 " + executable + "，请确认 PostgreSQL 客户端已安装，或在 S3 备份配置中填写正确路径", exception);
        }
        String output;

        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes());
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException(String.join(" ", command) + " 执行失败：" + output);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;

        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause.getMessage() != null && !rootCause.getMessage().isBlank()) {
            return rootCause.getMessage();
        }

        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            return throwable.getMessage();
        }

        return throwable.getClass().getSimpleName();
    }

    private PostgresConnectionInfo parsePostgresConnection() {
        try {
            URI uri = URI.create(datasourceUrl.substring("jdbc:".length()));
            String database = uri.getPath().replaceFirst("^/", "");
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            return new PostgresConnectionInfo(uri.getHost(), String.valueOf(port), database);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析 PostgreSQL 连接地址：" + datasourceUrl, exception);
        }
    }

    private BackupConfigEntity requireConfig() {
        BackupConfigEntity config = backupConfigRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("请先保存 S3 备份配置"));

        if (!StringUtils.hasText(config.getEndpoint()) || !StringUtils.hasText(config.getBucket())) {
            throw new IllegalStateException("S3 备份配置不完整");
        }

        return config;
    }

    private MinioClient createClient(BackupConfigEntity config) {
        return createClientBuilder(config).build();
    }

    private void validateCron(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cron 表达式无效", exception);
        }
    }

    private String normalizeCronExpression(String cronExpression) {
        String normalized = cronExpression.trim().replaceAll("\\s+", " ");

        if (normalized.split(" ").length == 5) {
            return "0 " + normalized;
        }

        return normalized;
    }

    private MinioClient.Builder createClientBuilder(BackupConfigEntity config) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey());

        if (StringUtils.hasText(config.getRegion())) {
            builder.region(config.getRegion());
        }

        return builder;
    }

    private void ensureBucket(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());

            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("S3 bucket 检查失败", exception);
        }
    }

    private void cleanupRetention(BackupConfigEntity config, MinioClient client) {
        List<BackupItemDto> backups = listBackups();
        int retentionCount = config.getRetentionCount() == null ? 7 : config.getRetentionCount();
        int retentionDays = config.getRetentionDays() == null ? 14 : config.getRetentionDays();
        Instant expireBefore = retentionDays <= 0 ? null : Instant.now().minusSeconds((long) retentionDays * 24 * 60 * 60);

        if (expireBefore != null) {
            backups.stream()
                    .filter((backup) -> backup.createdAt() != null && backup.createdAt().isBefore(expireBefore))
                    .forEach((backup) -> removeBackupQuietly(client, config, backup));
            backups = backups.stream()
                    .filter((backup) -> backup.createdAt() == null || !backup.createdAt().isBefore(expireBefore))
                    .toList();
        }

        if (retentionCount <= 0) {
            return;
        }

        if (backups.size() <= retentionCount) {
            return;
        }

        backups.stream().skip(retentionCount).forEach((backup) -> removeBackupQuietly(client, config, backup));
    }

    private void removeBackupQuietly(MinioClient client, BackupConfigEntity config, BackupItemDto backup) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(config.getBucket()).object(backup.objectName()).build());
        } catch (Exception ignored) {
            // Retention cleanup should not fail the successful backup.
        }
    }

    private void validateObjectName(BackupConfigEntity config, String objectName) {
        String prefix = normalizePrefix(config.getPrefix()) + "/";

        if (!StringUtils.hasText(objectName) || !objectName.startsWith(prefix) || !objectName.endsWith(".zip")) {
            throw new IllegalArgumentException("非法备份对象路径");
        }
    }

    private BackupConfigDto toDto(BackupConfigEntity config) {
        return new BackupConfigDto(
                config.isEnabled(),
                config.getEndpoint(),
                config.getBucket(),
                config.getAccessKey(),
                config.getRegion(),
                config.getPrefix(),
                config.getSchedule(),
                config.getRetentionDays(),
                config.getRetentionCount(),
                config.getLastBackupAt(),
                config.getUpdatedAt(),
                config.isPathStyleAccess(),
                StringUtils.hasText(config.getSecretKey()),
                normalizeCommandPath(config.getPgDumpPath(), "pg_dump"),
                normalizeCommandPath(config.getPsqlPath(), "psql")
        );
    }

    private String normalizePrefix(String prefix) {
        String normalized = StringUtils.hasText(prefix) ? prefix.trim() : "rag-study/backups";
        return normalized.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String normalizeCommandPath(String path, String defaultCommand) {
        if (!StringUtils.hasText(path)) {
            return defaultCommand;
        }

        return path.trim().replaceAll("^\"|\"$", "");
    }

    private void addBytes(ZipOutputStream zipOutputStream, String entryName, byte[] content) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private void addFile(ZipOutputStream zipOutputStream, String entryName, Path file) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zipOutputStream);
        zipOutputStream.closeEntry();
    }

    private void unzip(Path zipFile, Path targetDirectory) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path target = targetDirectory.resolve(entry.getName()).normalize();

                if (!target.startsWith(targetDirectory)) {
                    throw new IllegalStateException("备份文件包含非法路径");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());

                    try (OutputStream outputStream = Files.newOutputStream(target)) {
                        zipInputStream.transferTo(outputStream);
                    }
                }
            }
        }
    }

    private record PostgresConnectionInfo(String host, String port, String database) {
    }
}
