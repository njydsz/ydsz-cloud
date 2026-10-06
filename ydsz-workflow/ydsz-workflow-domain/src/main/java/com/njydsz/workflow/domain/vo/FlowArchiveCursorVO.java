package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 流程归档断点续传游标视图对象。
 *
 * <p>用于返回归档游标的展示数据（DDD-007：禁止将 Entity 泄露到 server 层）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class FlowArchiveCursorVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 游标主键 ID */
  private String id;

  /** 归档类型（INSTANCE / PURGE） */
  private String archiveType;

  /** 游标值（最大 end_time 或最大 id） */
  private String cursorValue;

  /** 附加数据 JSON */
  private String cursorData;

  /** 租户 ID */
  private String tenantId;
}
