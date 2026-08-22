package com.njydsz.workflow.domain.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程分类树形 VO，用于前端设计器左侧导航树渲染。
 *
 * <p>由 {@link com.njydsz.workflow.server.service.impl.FlowCategoryServiceImpl#tree(String)} 使用 {@link
 * com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 构建，自动填充 {@code level}/{@code path} 元数据。
 *
 * <p>与 {@link FlowCategoryVO} 的区别：
 *
 * <ul>
 *   <li>{@code children} — 子节点列表（递归嵌套）
 *   <li>{@code level} — 层级深度（根节点=1，逐层+1）
 *   <li>{@code path} — 节点路径（如 "/1/5/12/"）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowCategoryVO 扁平结构 VO
 * @see com.njydsz.common.domain.tree.TreeBuilder 通用树构建器
 */
@Data
@Schema(description = "流程分类树形结构")
public class FlowCategoryTreeVO {

  /** 分类唯一标识 */
  @Schema(description = "分类唯一标识")
  private String id;

  /** 父分类 ID（顶级分类为 "0" 或 null） */
  @Schema(description = "父分类 ID")
  private String parentId;

  /** 子分类列表 */
  @Schema(description = "子分类列表")
  private List<FlowCategoryTreeVO> children;

  /** 层级深度（根节点=1，由 TreeBuilder 自动填充） */
  @Schema(description = "层级深度")
  private Integer level;

  /** 节点路径（如 "/1/5/12/"，由 TreeBuilder 自动填充） */
  @Schema(description = "节点路径")
  private String path;

  /** 分类编码（唯一，业务语义，如 "HR"、"FINANCE"） */
  @Schema(description = "分类编码")
  private String categoryCode;

  /** 分类名称（前端展示） */
  @Schema(description = "分类名称")
  private String categoryName;

  /** 排序号（越小越靠前） */
  @Schema(description = "排序号")
  private Integer sortNum;

  /** 图标（前端展示用，如 Element Plus icon 名称） */
  @Schema(description = "图标")
  private String icon;

  /** 备注（说明分类的业务用途） */
  @Schema(description = "备注")
  private String remark;
}
