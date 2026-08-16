package com.njydsz.cronjob.server.service.impl.job;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.cronjob.domain.entity.job.JobArtifact;
import com.njydsz.cronjob.infra.mapper.job.JobArtifactMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务产物服务实现。
 *
 * <p>管理任务运行产出的文件（{@code ydsz_job_artifact}）：报表、导出、
 *
 * <p>临时下载链接。产物支持 OSS / 本地存储双后端。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobArtifactService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final JobArtifactMapper artifactMapper;

  /** P3-3.3: 制品存储配置统一从 CronjobProperties 读取 */
  private final CronjobProperties cronjobProperties;

  /**
   * 保存执行产物。
   *
   * @param jobId 任务 ID
   * @param logId 日志 ID
   * @param jobKey 任务 KEY
   * @param artifactName 产物名称
   * @param artifactType 产物类型
   * @param content 产物内容
   * @param contentType 内容类型
   * @param metadata 元数据 JSON
   * @return 产物记录 ID
   */
  public String saveArtifact(
      String jobId,
      String logId,
      String jobKey,
      String artifactName,
      String artifactType,
      byte[] content,
      String contentType,
      String metadata) {
    try {
      String storageDir = cronjobProperties.getArtifact().getStorageDir();
      int retentionDays = cronjobProperties.getArtifact().getRetentionDays();
      // 存储文件
      String relativePath = jobKey + "/" + logId + "/" + UUID.randomUUID() + "_" + artifactName;
      Path fullPath = Paths.get(storageDir, relativePath);
      Files.createDirectories(fullPath.getParent());
      Files.write(fullPath, content);

      // 记录元数据
      JobArtifact artifact = new JobArtifact();
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
      artifact.setDeleted(0);
      artifactMapper.insert(artifact);

      log.info(
          "[ArtifactService] 产物已保存: logId={} name={} size={}B",
          logId,
          artifactName,
          content.length);
      return artifact.getId();
    } catch (IOException e) {
      log.error(
          "[ArtifactService] 保存产物异常: logId={} name={} reason={}",
          logId,
          artifactName,
          e.getMessage(),
          e);
      return null;
    }
  }

  /** 查询任务执行产物列表。 */
  public List<JobArtifact> getArtifactsByLogId(String logId) {
    return artifactMapper.selectByLogId(logId);
  }

  /** 读取产物内容。 */
  public byte[] readArtifact(String artifactId) {
    JobArtifact artifact = artifactMapper.selectById(artifactId);
    if (artifact == null) {
      return null;
    }
    try {
      String storageDir = cronjobProperties.getArtifact().getStorageDir();
      Path fullPath = Paths.get(storageDir, artifact.getStoragePath());
      return Files.readAllBytes(fullPath);
    } catch (IOException e) {
      log.error("[ArtifactService] 读取产物异常: id={} reason={}", artifactId, e.getMessage(), e);
      return null;
    }
  }

  /** 清理过期产物。 */
  public int cleanExpiredArtifacts(int batchSize) {
    LocalDateTime before = LocalDateTime.now();
    int cleaned = artifactMapper.cleanExpired(before, batchSize);
    if (cleaned > 0) {
      log.info("[ArtifactService] 清理过期产物: count={}", cleaned);
    }
    return cleaned;
  }
}
