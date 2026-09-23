package com.njydsz.nextwiki.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

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
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 泛型擦除导致 unchecked 警告
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_wiki_space_template")
public class SpaceTemplate extends MpBaseAuditEntity<String> {

  /** 分类：通用 */
  public static final String CATEGORY_GENERAL = "general";

  /** 分类：项目 */
  public static final String CATEGORY_PROJECT = "project";

  /** 分类：会议 */
  public static final String CATEGORY_MEETING = "meeting";

  /** 分类：知识库 */
  public static final String CATEGORY_KNOWLEDGE = "knowledge";

  /** 主键ID（分布式ID手动赋值，覆盖基类 ASSIGN_ID）。 */
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
  private Boolean isSystem;

  /** 是否公开（所有租户可见） */
  private Boolean isPublicAccess;

  /** 模板结构 JSON（定义目录树、初始页面、权限配置等） */
  private String structureJson;

  /** 排序序号 */
  private Integer sort;

  /** 使用次数 */
  private Integer usageCount;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean isDeleted;

  // ==================== P2-3: 文件模板扩展 ====================

  /**
   * P2-3: 模板类型（space=空间模板，file=文件模板）。
   *
   * <p>空间模板用于创建整个空间结构（含目录树与初始页面）；文件模板用于基于现有文件快速创建新文件。
   */
  private String templateType;

  /** 模板类型：空间 */
  public static final String TEMPLATE_TYPE_SPACE = "space";

  /** 模板类型：文件 */
  public static final String TEMPLATE_TYPE_FILE = "file";

  /**
   * P2-3: 源文件节点 ID（templateType=file 时必填）。
   *
   * <p>标识该模板是基于哪个文件节点创建的，使用此模板时将复制该节点的存储对象创建新文件。
   */
  private String sourceNodeId;

  /**
   * P2-3: 可见性级别（system=系统内置，org=组织内可见，private=仅创建者可见）。
   *
   * <p>控制模板的查询范围与可使用人群，对标竞品（飞书模板市场/语雀模板中心）的三级可见性体系。
   */
  private String visibility;

  /** 可见性：系统内置（所有租户可见） */
  public static final String VISIBILITY_SYSTEM = "system";

  /** 可见性：组织内可见（同租户可见） */
  public static final String VISIBILITY_ORG = "org";

  /** 可见性：私有（仅创建者可见） */
  public static final String VISIBILITY_PRIVATE = "private";
}
