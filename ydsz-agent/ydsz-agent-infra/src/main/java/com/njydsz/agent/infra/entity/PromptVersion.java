package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Prompt 模板版本（映射 ydsz_prompt_version 表）
 *
 * <p>记录每次模板更新的历史快照，支持版本回滚。 每次对 {@code ydsz_prompt_template} 的更新操作均在此表追加一条记录。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变持久化实体； 仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_prompt_version")
public class PromptVersion extends MpBaseEntity<String> {

  /** 所属模板编码（关联 ydsz_prompt_template.template_code） */
  private String templateCode;

  /** 版本号（与 template 的 currentVersion 对应） */
  private Integer version;

  /** 该版本的模板内容快照 */
  private String content;

  /** 版本备注（描述本次变更内容） */
  private String changeNote;
}
