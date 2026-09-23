package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Prompt 模板版本持久化对象（映射 ydsz_agt_prompt_version 表）。
 *
 * <p>基础设施层 PO，包含 MyBatis-Plus 持久化注解。
 * 对应的领域模型 {@code domain.entity.PromptVersion} 为纯净 POJO。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_prompt_version")
public class PromptVersionPO extends MpBaseEntity<String> {

  /** 所属模板编码 */
  private String templateCode;

  /** 版本号 */
  private Integer version;

  /** 该版本的模板内容快照 */
  private String content;

  /** 版本备注（描述本次变更内容） */
  private String changeNote;
}
