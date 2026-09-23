package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.agent.entity.base.DomainBaseEntity;

/**
 * Prompt 模板版本（domain 纯净 POJO，无 MP 注解）
 *
 * <p>记录每次模板更新的历史快照，支持版本回滚。每次对 {@code ydsz_agt_prompt_template} 的更新操作均在此表追加一条记录。
 *
 * <p><b>DDD 分层</b>：domain 层不携带 MyBatis-Plus 注解；
 * 持久化映射由 {@code infra.entity.PromptVersionPO} 承担。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PromptVersion extends DomainBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 所属模板编码（关联 ydsz_agt_prompt_template.template_code） */
  private String templateCode;

  /** 版本号（与 template 的 currentVersion 对应） */
  private Integer version;

  /** 该版本的模板内容快照 */
  private String content;

  /** 版本备注（描述本次变更内容） */
  private String changeNote;
}
