package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 流程回滚结果视图对象。
 *
 * <p>用于返回流程版本回滚操作的结果，包含回滚前后的版本信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowRollbackResultVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 是否回滚成功 */
  private Boolean success;

  /** 结果描述信息 */
  private String message;

  /** 流程编码 */
  private String flowCode;

  /** 回滚前版本号 */
  private Integer fromVersion;

  /** 回滚目标版本号 */
  private Integer toVersion;
}
