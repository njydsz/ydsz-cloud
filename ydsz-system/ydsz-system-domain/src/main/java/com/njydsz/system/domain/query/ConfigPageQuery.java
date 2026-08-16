package com.njydsz.system.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.domain.query.PageQuery;

/**
 * 系统配置分页查询参数
 *
 * <p>对应 {@code ydsz_config} 表的分页查询条件，由 Controller 接收并透传给
 * {@code ConfigService.page()}。继承自 {@link PageQuery}，自带 {@code pageNum} /
 * {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@code configGroup} — 配置分组精确匹配（{@code =}），如 {@code ydsz.workflow}</li>
 *   <li>{@code configKey} — 配置键模糊匹配（{@code LIKE %xxx%}）</li>
 *   <li>{@code status} — 启用状态精确匹配（{@code =}），可空</li>
 * </ul>
 *
 * <p><b>多租户：</b>租户过滤由 MyBatis 拦截器自动注入。
 *
 * <p><b>索引利用：</b>{@code (configGroup, configKey)} 命中
 * {@code uk_tenant_group_key} 唯一索引的前缀；典型查询模式
 * 「按分组列出」走该索引范围扫描，性能可控。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 * @see com.njydsz.system.server.service.ConfigService 系统配置服务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统配置分页查询参数")
public class ConfigPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "配置分组")
    private String configGroup;

    @Schema(description = "配置键（模糊匹配）")
    private String configKey;

    @Schema(description = "启用状态：ENABLED/DISABLED")
    private String status;
}
