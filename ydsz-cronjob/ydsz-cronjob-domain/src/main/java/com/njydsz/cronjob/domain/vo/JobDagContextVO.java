package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * DAG 实例节点上下文 VO（P0-13 优化：节点级结果独立存储）。
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Data
public class JobDagContextVO {

  /** 主键 ID */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** DAG 实例 ID */
  private String dagInstanceId;

  /** 节点 KEY */
  private String nodeKey;

  /** 节点执行结果 JSON */
  private String resultJson;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
