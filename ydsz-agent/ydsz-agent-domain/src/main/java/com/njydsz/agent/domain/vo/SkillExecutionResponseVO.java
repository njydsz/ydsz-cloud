package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Skill 执行结果视图对象
 *
 * <p>用于 Controller 层返回 Skill 执行结果的展示数据。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
public class SkillExecutionResponseVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Skill 编码 */
  private String skillCode;

  /** 执行是否成功 */
  @JsonProperty("success")
  private boolean isSuccess;

  /** 标准输出内容 */
  private String stdout;

  /** 标准错误内容 */
  private String stderr;

  /** 产物文件列表 */
  private List<String> outputFiles;

  /** 执行指标（elapsedMs / exitCode 等） */
  private Map<String, Object> metrics;

  /** 失败原因 */
  private String errorMessage;

  /** 执行完成时间 */
  private LocalDateTime completedAt;
}
