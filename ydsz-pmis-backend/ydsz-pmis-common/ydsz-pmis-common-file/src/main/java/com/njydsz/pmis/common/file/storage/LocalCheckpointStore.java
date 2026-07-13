package com.njydsz.pmis.common.file.storage;

import java.io.File;
import java.nio.file.Files;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于本地文件的检查点存储实现（向后兼容）
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class LocalCheckpointStore implements CheckpointStore {

    /** 检查点文件存储根目录 */
    private final String baseDir;

    /**
     * 构造本地文件检查点存储
     *
     * @param baseDir 检查点文件存储根目录，为空时使用系统临时目录
     */
    public LocalCheckpointStore(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * 保存检查点到本地文件
     *
     * @param bucketName  存储桶名称
     * @param objectName  对象键
     * @param checkpoint  检查点 JSON 字符串
     * @param ttlSeconds  生存时间（秒），本地文件实现不使用此参数
     */
    @Override
    public void save(String bucketName, String objectName, String checkpoint, long ttlSeconds) {
        String key = buildKey(bucketName, objectName);
        try {
            File checkpointFile = new File(key);
            File parentDir = checkpointFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Files.writeString(checkpointFile.toPath(), checkpoint);
        } catch (Exception e) {
            log.warn("[Storage] LocalCheckpointStore save failed, path={}, message={}",
                    key, e.getMessage());
        }
    }

    /**
     * 从本地文件读取检查点
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象键
     * @return 检查点 JSON 字符串，不存在时返回 null
     */
    @Override
    public String get(String bucketName, String objectName) {
        String path = buildKey(bucketName, objectName);
        try {
            File checkpointFile = new File(path);
            if (!checkpointFile.exists()) {
                return null;
            }
            return Files.readString(checkpointFile.toPath());
        } catch (Exception e) {
            log.warn("[Storage] LocalCheckpointStore get failed, path={}, message={}",
                    path, e.getMessage());
            return null;
        }
    }

    /**
     * 删除本地检查点文件
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象键
     */
    @Override
    public void remove(String bucketName, String objectName) {
        String path = buildKey(bucketName, objectName);
        try {
            File checkpointFile = new File(path);
            if (checkpointFile.exists()) {
                checkpointFile.delete();
            }
        } catch (Exception e) {
            log.warn("[Storage] LocalCheckpointStore remove failed, path={}, message={}",
                    path, e.getMessage());
        }
    }

    @Override
    public String buildKey(String bucketName, String objectName) {
        String effectiveBaseDir = (baseDir != null && !baseDir.isBlank()) ? baseDir : System.getProperty("java.io.tmpdir");
        String safeBucket = bucketName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        String safeObjectName = objectName.replace("/", "_").replace("\\", "_").replaceAll("\\.\\.", "_");
        return effectiveBaseDir + File.separator + "upload_checkpoint_" + safeBucket + "_" + safeObjectName;
    }
}
