package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;


/**
 * 代码生成器模板分组领域实体。
 *
 * <p>对应 ydsz_gen_template_group 表。模板按技术栈/风格分组，
 * 如 default（标准 DDD）、mybatis-plus、mongodb 等。
 * 同一时间只有一个分组处于激活状态。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_template_group")
public class GenTemplateGroup extends MpBaseEntity<String> {

  /** 分组名（UNIQUE，如 default、mybatis-plus）。 */
  @NotBlank(message = "{generator.templateGroup.name.notblank}")
  @Size(max = 64, message = "{generator.templateGroup.name.size}")
  private String name;
  /** 分组描述。 */
  @Size(max = 255, message = "{generator.templateGroup.description.size}")
  private String description;
  /** 是否为系统分组（系统分组不可删除）。 */
  private Boolean isSystem;
  /** 是否激活为当前使用分组。 */
  @TableField("active_flag")
  private Boolean isActive;
}
