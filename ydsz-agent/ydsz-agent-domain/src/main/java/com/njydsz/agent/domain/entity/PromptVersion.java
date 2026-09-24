package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Prompt 模板版本（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>记录每次模板更新的历史快照，支持版本回滚。每次对 {@code ydsz_agt_prompt_template} 的更新操作均在此表追加一条记录。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_prompt_version")
public class PromptVersion extends MpBaseEntity<String> {

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
