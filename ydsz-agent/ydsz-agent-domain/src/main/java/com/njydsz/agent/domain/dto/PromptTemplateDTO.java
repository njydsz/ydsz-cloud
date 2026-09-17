package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Prompt 模板 DTO。
 *
 * <p>用于创建/更新 Prompt 模板，同时承载 API 响应返回的模板信息。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class PromptTemplateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID（更新时必填） */
  private String id;

  /** 模板唯一编码（业务标识，创建后不可变） */
  private String templateCode;

  /** 模板名称（展示用） */
  private String templateName;

  /** 模板内容，支持 #{var} 占位符 */
  private String content;

  /** 模板描述 */
  private String description;

  /** 分类（用于分组检索） */
  private String category;

  /** 当前版本号，自 1 起每次更新递增 */
  private Integer currentVersion;

  /** 是否启用 A/B 灰度测试 (`<!-- OOP-006-EXEMPT: DTO 字段 -->`：使用 is 前缀遵循规范) */
  private Boolean isAbTestEnabled;

  /** A/B 灰度目标版本（canary 版本号） */
  private Integer abTargetVersion;

  /** A/B 灰度流量百分比（1-100），表示路由到新版本的流量比例 */
  private Integer abTrafficPercent;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
