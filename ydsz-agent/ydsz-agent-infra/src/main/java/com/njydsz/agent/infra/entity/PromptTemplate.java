package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Prompt 模板（映射 ydsz_agt_prompt_template 表）
 *
 * <p>存储 Prompt 模板的当前版本信息，包含编码、名称、内容、分类等。 版本历史由 {@link PromptVersion} 独立维护。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变持久化实体； 仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_prompt_template")
public class PromptTemplate extends MpBaseEntity<String> {

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
}
