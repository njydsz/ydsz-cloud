package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P3-3.3: 任务制品（Artifact）存储配置。
 *
 * <p>控制 JobArtifactService 的制品文件存储目录和保留策略，超过 retentionDays 的制品文件由清理任务自动删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ArtifactConfig {

  /** 制品存储目录（默认 ./data/artifacts） */
  private String storageDir = "./data/artifacts";

  /** 制品保留天数（超过此天数的制品自动清理，默认 30 天） */
  private int retentionDays = 30;
}
