package com.njydsz.system.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 系统变量分页查询参数
 *
 * <p>对应 {@code ydsz_sys_variable} 表的分页查询条件。继承自 {@link PageQuery}，自带 {@code pageNum} /
 * {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code variableKey} — 变量键模糊匹配（可选）
 *   <li>{@code status} — 启用状态精确匹配（可选）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.domain.query.PageQuery 父类（分页参数）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VariablePageQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  private String variableKey;

  private String status;
}
