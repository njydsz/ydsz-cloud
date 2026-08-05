package com.remisoft.common.file.storage;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.file.domain.UploadCheckpoint;
import com.remisoft.common.file.exception.FileExceptionCode;
import com.remisoft.common.file.storage.IFileStorage.PartInfo;
import com.remisoft.common.json.RemiJson;
import com.remisoft.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认检查点服务实现
 * <p>从 {@link AbstractFileStorage} 中提取的检查点管理逻辑，
 * 封装 JSON 序列化、校验恢复、MD5 累积计算、文件完整性校验等业务逻辑。
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class DefaultCheckpointService implements CheckpointService {

    /**
     * 检查点 TTL（24 小时）
     */
    private static final long DEFAULT_CHECKPOINT_TTL_SECONDS = 24 * 3600;

    private final CheckpointStore checkpointStore;
    private final MultipartLister multipartLister;
    private final long checkpointTtlSeconds;

    /**
     * 构造检查点服务
     *
     * @param checkpointStore 检查点存储
     * @param multipartLister 分片列表查询器（用于恢复检查点）
     */
    public DefaultCheckpointService(CheckpointStore checkpointStore, MultipartLister multipartLister) {
        this(checkpointStore, multipartLister, DEFAULT_CHECKPOINT_TTL_SECONDS);
    }

    /**
     * 构造检查点服务
     *
     * @param checkpointStore        检查点存储
     * @param multipartLister        分片列表查询器（用于恢复检查点）
     * @param checkpointTtlSeconds   检查点 TTL（秒）
     */
    public DefaultCheckpointService(CheckpointStore checkpointStore, MultipartLister multipartLister, long checkpointTtlSeconds) {
        this.checkpointStore = checkpointStore;
        this.multipartLister = multipartLister;
        this.checkpointTtlSeconds = checkpointTtlSeconds;
    }

    @Override
    public void saveCheckpoint(UploadCheckpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        try {
            String json = RemiJson.toJson(checkpoint);
            checkpointStore.save(checkpoint.getBucketName(), checkpoint.getObjectName(), json, checkpointTtlSeconds);
        } catch (Exception e) {
            log.warn("[Storage] saveCheckpoint failed, bucket={}, object={}, message={}",
                    checkpoint.getBucketName(), checkpoint.getObjectName(), e.getMessage());
        }
    }

    @Override
    public UploadCheckpoint loadCheckpoint(String bucketName, String objectName) {
        try {
            String json = checkpointStore.get(bucketName, objectName);
            if (json != null) {
                return RemiJson.toObject(json, UploadCheckpoint.class);
            }
        } catch (Exception e) {
            log.warn("[Storage] loadCheckpoint failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
        }
        return null;
    }

    @Override
    public void deleteCheckpoint(UploadCheckpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        checkpointStore.remove(checkpoint.getBucketName(), checkpoint.getObjectName());
    }

    @Override
    public UploadCheckpoint validateAndRecoverCheckpoint(UploadCheckpoint checkpoint, MultipartFile file) {
        if (checkpoint.getTotalSize() == null || !checkpoint.getTotalSize().equals(file.getSize())) {
            log.warn("[Storage] checkpoint totalSize mismatch, expected={}, actual={}",
                    checkpoint.getTotalSize(), file.getSize());
            deleteCheckpoint(checkpoint);
            return null;
        }

        String uploadId = checkpoint.getUploadId();
        if (StringUtils.isBlank(uploadId)) {
            return null;
        }

        try {
            List<PartInfo> existingParts = multipartLister.listParts(
                    checkpoint.getBucketName(), checkpoint.getObjectName(), uploadId);
            if (existingParts.isEmpty()) {
                deleteCheckpoint(checkpoint);
                return null;
            }

            List<UploadCheckpoint.UploadedPart> recoveredParts = new ArrayList<>();
            long uploadedBytes = 0;
            for (PartInfo part : existingParts) {
                UploadCheckpoint.UploadedPart uploadedPart = new UploadCheckpoint.UploadedPart();
                uploadedPart.setPartNumber(part.partNumber());
                uploadedPart.setSize(part.size());
                uploadedPart.setETag(part.eTag());
                uploadedPart.setUploaded(true);
                recoveredParts.add(uploadedPart);
                uploadedBytes += part.size();
            }

            checkpoint.setUploadedParts(recoveredParts);
            checkpoint.setUploadedBytes(uploadedBytes);
            checkpoint.setUploadedPartsCount(recoveredParts.size());
            checkpoint.setLastModifyTime(LocalDateTime.now());
            saveCheckpoint(checkpoint);
            return checkpoint;
        } catch (Exception e) {
            log.warn("[Storage] validateAndRecoverCheckpoint failed, uploadId={}, message={}",
                    uploadId, e.getMessage());
            deleteCheckpoint(checkpoint);
            return null;
        }
    }

    @Override
    public void updateChunkMd5InCheckpoint(String bucketName, String objectName, int partNumber, String chunkMd5, long chunkSize) {
        try {
            UploadCheckpoint checkpoint = loadCheckpoint(bucketName, objectName);
            if (checkpoint == null) {
                return;
            }
            List<UploadCheckpoint.UploadedPart> parts = checkpoint.getUploadedParts();
            if (parts == null) {
                parts = new ArrayList<>();
                checkpoint.setUploadedParts(parts);
            }

            UploadCheckpoint.UploadedPart existingPart = null;
            for (UploadCheckpoint.UploadedPart part : parts) {
                if (part.getPartNumber() != null && part.getPartNumber() == partNumber) {
                    existingPart = part;
                    break;
                }
            }

            if (existingPart != null) {
                existingPart.setChunkMd5(chunkMd5);
            } else {
                UploadCheckpoint.UploadedPart newPart = new UploadCheckpoint.UploadedPart();
                newPart.setPartNumber(partNumber);
                newPart.setChunkMd5(chunkMd5);
                newPart.setSize(chunkSize);
                newPart.setUploaded(true);
                parts.add(newPart);
            }

            checkpoint.setUploadedPartsCount(parts.size());
            checkpoint.setLastModifyTime(LocalDateTime.now());
            saveCheckpoint(checkpoint);
        } catch (Exception e) {
            log.warn("[Storage] updateChunkMd5InCheckpoint failed, bucket={}, object={}, part={}, message={}",
                    bucketName, objectName, partNumber, e.getMessage());
        }
    }

    @Override
    public void updateAccumulatedMd5(String bucketName, String objectName, String accumulatedMd5) {
        try {
            UploadCheckpoint checkpoint = loadCheckpoint(bucketName, objectName);
            if (checkpoint != null) {
                checkpoint.setAccumulatedMd5Hex(accumulatedMd5);
                checkpoint.setLastModifyTime(LocalDateTime.now());
                saveCheckpoint(checkpoint);
            }
            log.debug("[Storage] updateAccumulatedMd5 success, bucket={}, object={}, md5={}",
                    bucketName, objectName, accumulatedMd5);
        } catch (Exception e) {
            log.warn("[Storage] updateAccumulatedMd5 failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
        }
    }

    @Override
    public void validateFileMd5(String bucketName, String objectName, boolean md5CheckEnabled,
                                 Md5Computer md5Computer, ObjectDownloader objectDownloader) {
        if (!md5CheckEnabled) {
            return;
        }

        try {
            UploadCheckpoint checkpoint = loadCheckpoint(bucketName, objectName);
            if (checkpoint == null || checkpoint.getFileMd5() == null) {
                return;
            }

            String actualMd5 = checkpoint.getAccumulatedMd5Hex();
            if (actualMd5 == null || actualMd5.isEmpty()) {
                log.warn("[Storage] accumulatedMd5Hex is null, falling back to re-download for MD5 validation, bucket={}, object={}",
                        bucketName, objectName);
                try (InputStream is = objectDownloader.download(bucketName, objectName)) {
                    actualMd5 = md5Computer.compute(is);
                    if (actualMd5 == null) {
                        log.warn("[Storage] validateFileMd5 compute failed, bucket={}, object={}", bucketName, objectName);
                        return;
                    }
                }
            }

            if (!actualMd5.equalsIgnoreCase(checkpoint.getFileMd5())) {
                log.error("[Storage] validateFileMd5 mismatch, bucket={}, object={}, expected={}, actual={}",
                        bucketName, objectName, checkpoint.getFileMd5(), actualMd5);
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
            }
            log.info("[Storage] validateFileMd5 success, bucket={}, object={}", bucketName, objectName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Storage] validateFileMd5 failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
        }
    }

    @Override
    public long getCheckpointTtlSeconds() {
        return checkpointTtlSeconds;
    }

    /**
     * 分片列表查询器接口
     * <p>用于解耦对底层存储的 listParts 依赖。
     */
    @FunctionalInterface
    public interface MultipartLister {
        List<PartInfo> listParts(String bucketName, String objectName, String uploadId);
    }
}
