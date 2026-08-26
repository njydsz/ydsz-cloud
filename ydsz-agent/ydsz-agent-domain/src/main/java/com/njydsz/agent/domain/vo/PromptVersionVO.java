package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Prompt 模板版本视图对象。
 *
 * <p>用于返回 Prompt 模板版本的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PromptVersionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 所属模板编码（关联 ydsz_agt_prompt_template.template_code） */
  private String templateCode;

  /** 版本号（与 template 的 currentVersion 对应） */
  private Integer version;

  /** 该版本的模板内容快照 */
  private String content;

  /** 版本备注（描述本次变更内容） */
  private String changeNote;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
