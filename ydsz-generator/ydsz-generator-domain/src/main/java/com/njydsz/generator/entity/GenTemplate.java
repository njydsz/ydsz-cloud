package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * 代码生成器模板领域实体。
 *
 * <p>对应 ydsz_gen_template 表，存储 Velocity 模板文件内容。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_template")
public class GenTemplate extends MpBaseEntity<String> {

  /** 所属模板分组 ID。 */
  private String groupId;
  /** 文件名（如 entity.vm、vue/api.vm）。 */
  private String fileName;
  /** 模板用途描述。 */
  private String description;
  /** 模板内容（Velocity 语法）。 */
  private String content;
  /** 是否为虚拟文件夹标记。 */
  private Boolean isFolder;
  /** 父路径（如 vue/ 表示前端子目录）。 */
  private String parentPath;
  /** 当前版本号。 */
  private Integer version;
  /** 内容 MD5 哈希（版本对比）。 */
  private String hash;
  /** 是否启用。 */
  @TableField("is_active")
  private Boolean isActive;
  /** 模板类型码（BACKEND/FRONTEND，对应 TemplateFileTypeEnum.code）。 */
  private String fileType;
}
