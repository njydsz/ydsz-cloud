package com.remisoft.common.file.storage.platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletResponse;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.file.config.FileProperties;
import com.remisoft.common.file.config.FileUploadProperties;
import com.remisoft.common.file.constant.FileConstant;
import com.remisoft.common.file.domain.ChunkedUploadResult;
import com.remisoft.common.file.domain.ListObjectsResult;
import com.remisoft.common.file.domain.ObjectMetadata;
import com.remisoft.common.file.domain.PolicyResult;
import com.remisoft.common.file.exception.FileExceptionCode;
import com.remisoft.common.file.storage.AbstractFileStorage;
import com.remisoft.common.util.io.IOUtils;
import com.remisoft.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;
/**
 * 本地磁盘存储实现
 * <p>继承 {@link AbstractFileStorage}，
 * 将文件存储到配置指定的本地目录（bucket 路径）。
 *
 * <p>通过 Nginx/网关将 domain 配置的访问域名映射到本地目录，实现类似对象存储的公开访问。
 * 分片上传使用本地文件系统暂存 + 按序合并策略，失败后自动清理临时分片目录。
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class LocalStorage extends AbstractFileStorage {

    /**
     * uploadId 校验规则（白名单字符 + 长度限制），用于阻断路径注入与非法参数
     */
    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile("^[0-9a-zA-Z-]{1,64}$");

    /**
     * 本地存储根目录（对应 FileProperties.bucket）
     */
    private final String directory;

    /**
     * 本地服务端点（用于拼接默认访问地址）
     */
    private final String endPoint;

    /**
     * 对外访问域名（通常由 Nginx/网关转发到本地目录）
     */
    private final String nginxUrl;

    /**
     * 构建本地存储客户端
     *
     * @param config 存储配置
     */
    public LocalStorage(FileProperties config) {
        this(config, null);
    }

    /**
     * 构建本地存储客户端
     *
     * @param config 存储配置
     * @param uploadProps 分片上传配置
     */
    public LocalStorage(FileProperties config, FileUploadProperties uploadProps) {
        super(config, uploadProps);
        try {
            this.directory = config.getBucket();
            this.endPoint = config.getEndpoint();
            this.nginxUrl = config.getDomain();
        } catch (Exception e) {
            log.error("[Local] LocalStorage build failed: {}", e.getMessage());
            throw new BusinessException(FileExceptionCode.STORAGE_CLIENT_BUILD_FAILED);
        }
    }

    @Override
    protected boolean doBucketExists(String bucketName) {
        File file = new File(bucketName);
        return file.exists() && file.isDirectory();
    }

    @Override
    protected void doMakeBucket(String bucketName) {
        File file = new File(bucketName);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created) {
                throw new BusinessException(FileExceptionCode.BUCKET_CREATE_FAILED);
            }
        }
    }

    @Override
    protected boolean doFolderExists(String bucketName, String folderName) {
        Path path = resolveLocalPath(bucketName, folderName);
        return Files.exists(path) && Files.isDirectory(path);
    }

    @Override
    protected void doMakeFolder(String bucketName, String folderName) {
        try {
            Path path = resolveLocalPath(bucketName, folderName);
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("[Local] makeFolder Exception:{}", e.getMessage());
            throw new BusinessException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    protected void doPutObject(String bucketName, String objectName,
                              InputStream inputStream, long size, String contentType) {
        Path targetPath = resolveLocalPath(bucketName, objectName);
        try {
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(targetPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                IOUtils.copy(inputStream, os);
            }
        } catch (IOException e) {
            log.error("[Local] doPutObject failed, object={}, message={}", objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    protected InputStream doGetObject(String bucketName, String objectName,
                                      Long offset, Long length) {
        try {
            Path file = resolveLocalPath(bucketName, objectName);
            if (!Files.exists(file)) {
                throw new BusinessException(FileExceptionCode.FILE_NOT_FOUND);
            }
            InputStream rawStream = Files.newInputStream(file);
            if (offset != null && offset >= 0) {
                long skipped = rawStream.skip(offset);
                if (skipped < offset) {
                    rawStream.close();
                    throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
                }
            }
            if (length != null && length > 0) {
                final InputStream delegate = rawStream;
                return new InputStream() {
                    private long remaining = length;
                    @Override
                    public int read() throws IOException {
                        if (remaining <= 0) return -1;
                        int b = delegate.read();
                        if (b != -1) remaining--;
                        return b;
                    }
                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        if (remaining <= 0) return -1;
                        int toRead = (int) Math.min(len, remaining);
                        int read = delegate.read(b, off, toRead);
                        if (read > 0) remaining -= read;
                        return read;
                    }
                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
            return rawStream;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("[Local] doGetObject failed, object={}, message={}", objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    protected void doRemoveObject(String bucketName, String objectName) {
        Path file = resolveLocalPath(bucketName, objectName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("[Local] doRemoveObject failed, object={}, message={}", objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    protected String buildObjectUrl(String bucketName, String objectName) {
        if (StringUtils.isNotBlank(nginxUrl)) {
            if (nginxUrl.endsWith("/")) {
                return nginxUrl + objectName;
            }
            return nginxUrl + "/" + objectName;
        }
        return endPoint + FileConstant.LOCAL_DIRECTORY_MAPPING + objectName;
    }

    @Override
    protected String buildPrivateUrl(String bucketName, String objectName) {
        return "";
    }

    @Override
    protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
    }

    @Override
    protected void doUploadPart(String bucketName, String chunkObjectName,
                               String uploadId, int partNumber,
                               InputStream inputStream, long size) {
        try {
            if (!UPLOAD_ID_PATTERN.matcher(uploadId).matches()) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
            }
            Path chunkPath = resolveLocalPath(bucketName, chunkObjectName);
            Path parent = chunkPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(chunkPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                IOUtils.copy(inputStream, os);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Local] doUploadPart failed, chunk={}, message={}", chunkObjectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doCompleteMultipartUpload(String bucketName, String objectName,
                                            String uploadId, List<Integer> partNumbers) {
        Path targetPath = resolveLocalPath(bucketName, objectName);
        Path parent = targetPath.getParent();
        Path uploadRootPath = resolveUploadRootPath(bucketName, objectName, uploadId);

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(targetPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Integer partNumber : partNumbers) {
                    Path chunkPath = resolveChunkPath(bucketName, objectName, uploadId, partNumber);
                    if (!Files.exists(chunkPath)) {
                        throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
                    }
                    try (InputStream is = Files.newInputStream(chunkPath)) {
                        IOUtils.copy(is, os);
                    }
                    Files.deleteIfExists(chunkPath);
                }
            }
            deleteIfEmpty(uploadRootPath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Local] doCompleteMultipartUpload failed, object={}, message={}", objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    @Override
    protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        Path uploadRootPath = resolveUploadRootPath(bucketName, objectName, uploadId);
        deleteIfEmpty(uploadRootPath);
    }

    @Override
    protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        Path uploadRootPath = resolveUploadRootPath(bucketName, objectName, uploadId);

        if (!Files.exists(uploadRootPath)) {
            return parts;
        }

        try (Stream<Path> stream = Files.list(uploadRootPath)) {
            stream.filter(p -> p.getFileName().toString().startsWith("part-"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        try {
                            int partNumber = Integer.parseInt(fileName.substring("part-".length()));
                            long size = Files.size(p);
                            parts.add(new PartInfo(partNumber, null, size));
                        } catch (NumberFormatException ignored) {
                            log.debug("Caught exception (ignored): {}", ignored.getMessage());
                        } catch (IOException e) {
                            log.warn("[Local] listParts get size failed, path={}, message={}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[Local] listParts failed, uploadRoot={}, message={}", uploadRootPath, e.getMessage());
        }
        return parts;
    }

    @Override
    protected ObjectMetadata doGetMetadata(String bucketName, String objectName) {
        try {
            Path file = resolveLocalPath(bucketName, objectName);
            if (!Files.exists(file)) {
                return null;
            }

            File localFile = file.toFile();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectName(objectName);
            metadata.setBucketName(bucketName);
            metadata.setSize(localFile.length());
            metadata.setLastModified(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(localFile.lastModified()),
                    ZoneId.systemDefault()));
            metadata.setContentType(Files.probeContentType(file));
            metadata.setIsDirectory(localFile.isDirectory());
            return metadata;
        } catch (Exception e) {
            log.error("[Local] doGetMetadata failed, object={}, message={}", objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.OBJECT_METADATA_GET_FAILED);
        }
    }

    @Override
    protected ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        List<ObjectMetadata> objects = new ArrayList<>();
        Path bucketPath = resolveLocalPath(bucketName, "");

        if (!Files.exists(bucketPath) || !Files.isDirectory(bucketPath)) {
            return ListObjectsResult.empty();
        }

        try {
            String resolvedPrefix = (prefix != null && !prefix.isEmpty()) ? prefix : "";
            if (resolvedPrefix.startsWith("/")) {
                resolvedPrefix = resolvedPrefix.substring(1);
            }
            Path searchPath = bucketPath.resolve(resolvedPrefix).normalize();

            if (!searchPath.startsWith(bucketPath)) {
                throw new BusinessException(FileExceptionCode.FILE_PATH_ILLEGAL);
            }

            try (Stream<Path> stream = Files.walk(searchPath, 1)) {
                List<Path> sortedFiles = stream.filter(Files::isRegularFile)
                        .sorted((p1, p2) -> {
                            String k1 = bucketPath.relativize(p1).toString().replace("\\", "/");
                            String k2 = bucketPath.relativize(p2).toString().replace("\\", "/");
                            return k1.compareTo(k2);
                        })
                        .collect(Collectors.toList());

                int startIndex = 0;
                if (cursor != null && !cursor.isEmpty()) {
                    String normalizedCursor = cursor.startsWith("/") ? cursor.substring(1) : cursor;
                    for (int i = 0; i < sortedFiles.size(); i++) {
                        String key = bucketPath.relativize(sortedFiles.get(i)).toString().replace("\\", "/");
                        if (key.compareTo(normalizedCursor) > 0) {
                            startIndex = i;
                            break;
                        }
                    }
                }

                String nextCursor = null;
                int endIndex = Math.min(startIndex + maxKeys, sortedFiles.size());
                for (int i = startIndex; i < endIndex; i++) {
                    Path path = sortedFiles.get(i);
                    try {
                        ObjectMetadata om = new ObjectMetadata();
                        om.setObjectName(bucketPath.relativize(path).toString().replace("\\", "/"));
                        om.setBucketName(bucketName);
                        om.setSize(Files.size(path));
                        om.setLastModified(LocalDateTime.ofInstant(
                                 Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()),
                                 ZoneId.systemDefault()));
                        om.setContentType(Files.probeContentType(path));
                        om.setIsDirectory(false);
                        objects.add(om);
                    } catch (IOException e) {
                        log.warn("[Local] 读取文件元数据失败，跳过该文件 | path={} | error={}", path, e.getMessage());
                    }
                }

                if (endIndex < sortedFiles.size()) {
                    nextCursor = bucketPath.relativize(sortedFiles.get(endIndex)).toString().replace("\\", "/");
                }

                ListObjectsResult result = new ListObjectsResult();
                result.setObjects(objects);
                result.setHasMore(nextCursor != null);
                result.setNextCursor(nextCursor);
                result.setObjectCount(objects.size());
                return result;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Local] doListObjects failed, bucket={}, prefix={}, message={}", bucketName, prefix, e.getMessage());
            throw new BusinessException(FileExceptionCode.OBJECT_LIST_FAILED);
        }
    }

    @Override
    public void download(String bucketName, String objectName, HttpServletResponse response, Long offset, Long length) {
        try {
            String resolvedBucket = resolveBucketName(bucketName);
            String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
            Path file = resolveLocalPath(resolvedBucket, resolvedObjectName);
            long fileSize = Files.size(file);
            long start = (offset != null && offset >= 0) ? offset : 0;
            long end = (length != null && length > 0) ? Math.min(start + length, fileSize) : fileSize;

            if (start >= fileSize) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            response.setStatus(start == 0 && (length == null || length >= fileSize)
                    ? HttpServletResponse.SC_OK : HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(end - start));
            response.setHeader("Content-Range", "bytes " + start + "-" + (end - 1) + "/" + fileSize);

            try (InputStream is = Files.newInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                is.skip(start);
                byte[] buffer = new byte[8192];
                long remaining = end - start;
                int bytesRead;
                while (remaining > 0 && (bytesRead = is.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    os.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
                os.flush();
            }
            log.info("[Local] file download success, object={}, offset={}, length={}", objectName, start, end - start);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Local] file download failed, object={}, message={}", objectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 解析本地文件存储路径，并校验路径安全性（防止路径穿越）
     *
     * @param bucketName 存储桶名称（对应本地目录路径）
     * @param objectName 对象键（文件相对路径）
     * @return 解析后的绝对路径
     * @throws BusinessException 路径非法（穿越根目录）时抛出
     */
    private Path resolveLocalPath(String bucketName, String objectName) {
        String resolvedBucket = StringUtils.isNotBlank(bucketName) ? bucketName : directory;
        String resolvedObject = StringUtils.isNotBlank(objectName) ? objectName : "";

        Path rootPath = Paths.get(resolvedBucket).toAbsolutePath().normalize();
        if (resolvedObject.startsWith("/")) {
            resolvedObject = resolvedObject.substring(1);
        }
        Path targetPath = rootPath.resolve(resolvedObject).normalize();

        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException(FileExceptionCode.FILE_PATH_ILLEGAL);
        }
        return targetPath;
    }

    /**
     * 解析分片文件的本地存储路径
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象键
     * @param uploadId   分片上传会话 ID
     * @param partNumber 分片序号
     * @return 分片文件的绝对路径
     */
    private Path resolveChunkPath(String bucketName, String objectName, String uploadId, int partNumber) {
        String chunkObjectName = CHUNK_DIR_PREFIX
                + FileConstant.DIR_SPLIT
                + objectName
                + FileConstant.DIR_SPLIT
                + uploadId
                + FileConstant.DIR_SPLIT
                + "part-" + partNumber;
        return resolveLocalPath(bucketName, chunkObjectName);
    }

    /**
     * 解析分片上传的根目录路径
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象键
     * @param uploadId   分片上传会话 ID
     * @return 分片上传根目录的绝对路径
     */
    private Path resolveUploadRootPath(String bucketName, String objectName, String uploadId) {
        String chunkRootObjectName = CHUNK_DIR_PREFIX
                + FileConstant.DIR_SPLIT
                + objectName
                + FileConstant.DIR_SPLIT
                + uploadId;
        return resolveLocalPath(bucketName, chunkRootObjectName);
    }

    /**
     * 删除空目录，递归向上清理直到遇到非空目录或分片根目录
     *
     * @param directoryPath 要检查和删除的目录路径
     */
    private void deleteIfEmpty(Path directoryPath) {
        try {
            if (!Files.exists(directoryPath)) {
                return;
            }
            if (hasChildren(directoryPath)) {
                return;
            }
            Files.deleteIfExists(directoryPath);
            Path parent = directoryPath.getParent();
            while (parent != null) {
                if (hasChildren(parent)) {
                    break;
                }
                String fileName = parent.getFileName() != null ? parent.getFileName().toString() : "";
                Files.deleteIfExists(parent);
                if (CHUNK_DIR_PREFIX.equals(fileName)) {
                    break;
                }
                parent = parent.getParent();
            }
        } catch (Exception e) {
            log.warn("[Local] delete empty chunk dir failed, path={}, message={}", directoryPath, e.getMessage());
        }
    }

    /**
     * 判断目录是否包含子项
     *
     * @param path 目录路径
     * @return true 表示目录非空，false 表示目录为空或不存在
     * @throws IOException 读取目录时发生 I/O 异常
     */
    private boolean hasChildren(Path path) throws IOException {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(path)) {
            return stream.findAny().isPresent();
        }
    }

    @Override
    public PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires) {
        log.warn("[Local] generateUploadPolicy is not supported for local storage");
        return null;
    }
}