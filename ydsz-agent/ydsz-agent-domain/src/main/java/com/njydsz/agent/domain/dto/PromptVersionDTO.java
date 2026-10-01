package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.safe.annotation.Xss;
import lombok.Data;

/**
 * Prompt 模板版本 DTO。
 *
 * <p>用于创建 Prompt 版本快照。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class PromptVersionDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 所属模板编码（关联 ydsz_agt_prompt_template.template_code） */
  private String templateCode;

  /** 版本号（与 template 的 currentVersion 对应） */
  private Integer version;

  /** 该版本的模板内容快照 */
  @Xss(message = "模板内容包含非法字符")
  private String content;

  /** 版本备注（描述本次变更内容） */
  @Xss(message = "版本备注包含非法字符")
  private String changeNote;
}
