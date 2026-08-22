package com.njydsz.userinfo.domain.vo;

import java.util.List;

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
 * <p><b>架构说明：</b>本类仅保留纯业务字段，Swagger 注解下沉至 Web 层的 CompanyTreeResponse DTO，
 * 避免 domain 层对 swagger-annotations 的编译期依赖（符合 DDD 分层纯净性约束）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CompanyVO 扁平结构 VO
 * @see com.njydsz.common.domain.tree.TreeBuilder 通用树构建器
 */
@Data
public class CompanyTreeVO {

  /** 公司唯一标识 */
  private String id;

  /** 上级公司 ID（顶级公司为 "0" 或 null） */
  private String parentId;

  /** 子公司列表 */
  private List<CompanyTreeVO> children;

  /** 层级深度（根节点=1，由 TreeBuilder 自动填充） */
  private Integer level;

  /** 节点路径（如 "/1/5/12/"，由 TreeBuilder 自动填充） */
  private String path;

  /** 公司名称（前端展示） */
  private String companyName;

  /** 公司编码（业务侧引用，全局唯一） */
  private String companyCode;

  /** 联系人姓名 */
  private String contactPerson;

  /** 联系电话 */
  private String contactPhone;

  /** 注册地址 */
  private String address;

  /** 启用状态（ENABLED / DISABLED） */
  private String status;
}
