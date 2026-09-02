package com.njydsz.system.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 字典类型分页查询参数
 *
 * <p>对应 {@code ydsz_sys_dict_type} 表的分页查询条件，由 Controller 接收并透传给 {@code DictTypeService.page()}。继承自
 * {@link PageQuery}，自带 {@code pageNum} / {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 类型编码精确匹配（{@code =}），用于「按编码快速定位」
 *   <li>{@code typeName} — 类型名称模糊匹配（{@code LIKE %xxx%}），用于「按名称搜索」
 *   <li>{@code status} — 启用状态精确匹配（{@code =}），可空（{@code null} 表示全部）
 * </ul>
 *
 * <p><b>多租户：</b>租户过滤由 MyBatis 拦截器（{@code ydsz-common-jdbc}）自动注入， 本类无需显式声明 {@code tenantId}。
 *
 * <p><b>索引利用：</b>{@code typeCode} 命中 {@code uk_tenant_type_code} 唯一索引； {@code typeName}
 * 模糊匹配走全表扫描（数据量可控，小于 1000 条无影响）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DictPageQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  private String typeCode;

  private String typeName;

  private String status;
}
