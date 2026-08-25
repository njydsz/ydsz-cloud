package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 公司实体
 *
 * <p>对应数据库表 {@code ydsz_company}，存储组织架构的最高级单位。 一个公司可包含多个部门（{@link Department}），通过 {@link
 * CompanyDept} 中间表维护多对多关系。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code companyCode}：公司编码（业务侧引用，全局唯一）
 *   <li>{@code parentId}：上级公司 ID（支持集团-子公司多级架构，{@code "0"} = 顶级公司）
 *   <li>{@code contactPerson} / {@code contactPhone}：对外联系信息
 *   <li>{@code address}：公司注册地址
 * </ul>
 *
 * <p><b>与租户（{@code Tenant}）的关系：</b>
 *
 * <ul>
 *   <li>公司 = 法人实体（合同主体、注册信息）
 *   <li>租户 = SaaS 系统隔离单位（数据隔离、计费单元）
 *   <li>一个公司可对应多个租户（如集团-子公司模式），一个租户通常归属一个公司
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_company_code}（{@code company_code}）， 普通索引 {@code
 * idx_parent_id}（{@code parent_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CompanyDept 公司-部门中间表
 * @see Department 部门实体
 * @see com.njydsz.userinfo.web.controller.CompanyController 公司 Controller
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_company")
public class Company extends MpBaseEntity<String> {

  /** 公司名称（前端展示） */
  private String companyName;

  /** 公司编码（业务侧引用，全局唯一，建议格式 {@code COMP_XXX}） */
  private String companyCode;

  /**
   * 上级公司 ID。
   *
   * <p>支持集团-子公司多级架构，{@code "0"} = 顶级公司。 顶级公司无 {@code parentId}，可独立管理下属子公司与部门。
   */
  private String parentId;

  /** 联系人姓名 */
  private String contactPerson;

  /** 联系电话 */
  private String contactPhone;

  /** 注册地址 */
  private String address;

  /**
   * 启用状态（{@code "ENABLED"} / {@code "DISABLED"}）
   *
   * <p>禁用后，公司下所有部门和用户均无法登录（由登录拦截器在租户上下文检查）。
   */
  private String status;
}
