package com.njydsz.userinfo.domain.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 公司树形 VO，用于前端集团-子公司组织架构树渲染。
 *
 * <p>由 {@link com.njydsz.userinfo.server.service.impl.CompanyServiceImpl#tree()} 使用 {@link
 * com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 构建，自动填充 {@code level}/{@code path} 元数据。
 *
 * <p>与 {@link CompanyVO} 的区别：
 *
 * <ul>
 *   <li>{@code children} — 子公司节点列表（递归嵌套）
 *   <li>{@code level} — 层级深度（根节点=1，逐层+1）
 *   <li>{@code path} — 节点路径（如 "/1/5/12/"）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.7.0
 * @see CompanyVO 扁平结构 VO
 * @see com.njydsz.common.domain.tree.TreeBuilder 通用树构建器
 */
@Data
@Schema(description = "公司树形结构")
public class CompanyTreeVO {

  /** 公司唯一标识 */
  @Schema(description = "公司唯一标识")
  private String id;

  /** 上级公司 ID（顶级公司为 "0" 或 null） */
  @Schema(description = "上级公司 ID")
  private String parentId;

  /** 子公司列表 */
  @Schema(description = "子公司列表")
  private List<CompanyTreeVO> children;

  /** 层级深度（根节点=1，由 TreeBuilder 自动填充） */
  @Schema(description = "层级深度")
  private Integer level;

  /** 节点路径（如 "/1/5/12/"，由 TreeBuilder 自动填充） */
  @Schema(description = "节点路径")
  private String path;

  /** 公司名称（前端展示） */
  @Schema(description = "公司名称")
  private String companyName;

  /** 公司编码（业务侧引用，全局唯一） */
  @Schema(description = "公司编码")
  private String companyCode;

  /** 联系人姓名 */
  @Schema(description = "联系人")
  private String contactPerson;

  /** 联系电话 */
  @Schema(description = "联系电话")
  private String contactPhone;

  /** 注册地址 */
  @Schema(description = "地址")
  private String address;

  /** 启用状态（ENABLED / DISABLED） */
  @Schema(description = "状态")
  private String status;
}
