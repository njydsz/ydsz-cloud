package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Prompt 模板视图对象。
 *
 * <p>用于返回 Prompt 模板的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class PromptTemplateVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
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

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
