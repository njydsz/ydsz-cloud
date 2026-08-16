package com.njydsz.system.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.domain.query.PageQuery;

/**
 * 字典类型分页查询参数
 *
 * <p>对应 {@code ydsz_dict_type} 表的分页查询条件，由 Controller 接收并透传给
 * {@code DictTypeService.page()}。继承自 {@link PageQuery}，自带 {@code pageNum} /
 * {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@code typeCode} — 类型编码精确匹配（{@code =}），用于「按编码快速定位」</li>
 *   <li>{@code typeName} — 类型名称模糊匹配（{@code LIKE %xxx%}），用于「按名称搜索」</li>
 *   <li>{@code status} — 启用状态精确匹配（{@code =}），可空（{@code null} 表示全部）</li>
 * </ul>
 *
 * <p><b>多租户：</b>租户过滤由 MyBatis 拦截器（{@code ydsz-common-jdbc}）自动注入，
 * 本类无需显式声明 {@code tenantId}。
 *
 * <p><b>索引利用：</b>{@code typeCode} 命中 {@code uk_tenant_type_code} 唯一索引；
 * {@code typeName} 模糊匹配走全表扫描（数据量可控，小于 1000 条无影响）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 * @see com.njydsz.system.server.service.DictTypeService 字典类型服务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型分页查询参数")
public class DictPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "类型编码（精确匹配）")
    private String typeCode;

    @Schema(description = "类型名称（模糊匹配）")
    private String typeName;

    @Schema(description = "启用状态：ENABLED/DISABLED")
    private String status;
}
