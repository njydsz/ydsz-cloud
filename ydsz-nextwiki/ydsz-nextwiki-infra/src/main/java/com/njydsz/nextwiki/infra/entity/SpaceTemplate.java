package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 空间模板持久化实体
 *
 * <p><b>S4-P3-02：文档模板体系</b>
 *
 * <p>对应空间模板表 {@code nw_space_template}，预定义可复用的空间结构模板（如"项目管理模板"、"会议纪要模板"）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_wiki_space_template")
public class SpaceTemplate implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 分类：通用 */
  public static final String CATEGORY_GENERAL = "general";

  /** 分类：项目 */
  public static final String CATEGORY_PROJECT = "project";

  /** 分类：会议 */
  public static final String CATEGORY_MEETING = "meeting";

  /** 分类：知识库 */
  public static final String CATEGORY_KNOWLEDGE = "knowledge";

  /** 主键ID（分布式雪花ID） */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 模板名称 */
  private String name;

  /** 模板描述 */
  private String description;

  /** 模板分类：general / project / meeting / knowledge */
  private String category;

  /** 模板图标 URL */
  private String iconUrl;

  /** 租户ID（系统模板为 null） */
  private String tenantId;

  /** 是否为系统内置模板（不可删除） */
  private Boolean systemFlag;

  /** 是否公开（所有租户可见） */
  private Boolean publicAccess;

  /** 模板结构 JSON（定义目录树、初始页面、权限配置等） */
  private String structureJson;

  /** 排序序号 */
  private Integer sortOrder;

  /** 使用次数 */
  private Integer usageCount;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 创建人 */
  private String createdBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 更新人 */
  private String updatedBy;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean deleted;
}
