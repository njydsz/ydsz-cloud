package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.cronjob.entity.JobArtifactDO;
import com.njydsz.pmis.cronjob.mapper.JobArtifactMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 执行产物管理服务（P2-8）。
 *
 * <p>提供执行产物的存储、查询、下载和清理能力。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobArtifactService {

    private final JobArtifactMapper artifactMapper;

    @Value("${pmis.cronjob.artifact.storage-dir:./data/artifacts}")
    private String storageDir;

    @Value("${pmis.cronjob.artifact.retention-days:30}")
    private int retentionDays;

    /**
     * 保存执行产物。
     *
     * @param jobId         任务 ID
     * @param logId         日志 ID
     * @param jobKey        任务 KEY
     * @param artifactName  产物名称
     * @param artifactType  产物类型
     * @param content       产物内容
     * @param contentType   内容类型
     * @param metadata      元数据 JSON
     * @return 产物记录 ID
     */
    public String saveArtifact(String jobId, String logId, String jobKey,
                                String artifactName, String artifactType,
                                byte[] content, String contentType, String metadata) {
        try {
            // 存储文件
            String relativePath = jobKey + "/" + logId + "/" + UUID.randomUUID() + "_" + artifactName;
            Path fullPath = Paths.get(storageDir, relativePath);
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, content);

            // 记录元数据
            JobArtifactDO artifact = new JobArtifactDO();
            artifact.setJobId(jobId);
            artifact.setLogId(logId);
            artifact.setJobKey(jobKey);
            artifact.setArtifactName(artifactName);
            artifact.setArtifactType(artifactType);
            artifact.setStoragePath(relativePath);
            artifact.setSizeBytes((long) content.length);
            artifact.setContentType(contentType);
            artifact.setMetadata(metadata);
            artifact.setExpireAt(LocalDateTime.now().plusDays(retentionDays));
            artifact.setCreatedAt(LocalDateTime.now());
            artifact.setDeleted(0);
            artifactMapper.insert(artifact);

            log.info("[ArtifactService] 产物已保存: logId={} name={} size={}B", logId, artifactName, content.length);
            return artifact.getId();
        } catch (IOException e) {
            log.error("[ArtifactService] 保存产物异常: logId={} name={} reason={}", logId, artifactName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 查询任务执行产物列表。
     */
    public List<JobArtifactDO> getArtifactsByLogId(String logId) {
        return artifactMapper.selectByLogId(logId);
    }

    /**
     * 读取产物内容。
     */
    public byte[] readArtifact(String artifactId) {
        JobArtifactDO artifact = artifactMapper.selectById(artifactId);
        if (artifact == null) {
            return null;
        }
        try {
            Path fullPath = Paths.get(storageDir, artifact.getStoragePath());
            return Files.readAllBytes(fullPath);
        } catch (IOException e) {
            log.error("[ArtifactService] 读取产物异常: id={} reason={}", artifactId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 清理过期产物。
     */
    public int cleanExpiredArtifacts(int batchSize) {
        LocalDateTime before = LocalDateTime.now();
        int cleaned = artifactMapper.cleanExpired(before, batchSize);
        if (cleaned > 0) {
            log.info("[ArtifactService] 清理过期产物: count={}", cleaned);
        }
        return cleaned;
    }
}
