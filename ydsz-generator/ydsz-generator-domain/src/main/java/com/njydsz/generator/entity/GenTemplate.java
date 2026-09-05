package com.njydsz.generator.entity;

import com.njydsz.generator.enums.TemplateFileTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 代码生成器模板实体。
 *
 * <p>存储 Velocity 模板文件内容，支持在线编辑与版本管理。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenTemplate {

  /** 模板 ID。 */
  private Long id;
  /** 所属模板分组 ID。 */
  private Long groupId;
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
  private Boolean isActive;
  /** 模板类型。 */
  private TemplateFileTypeEnum fileType;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
