package com.njydsz.system.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 字典项分页查询参数
 *
 * <p>对应 {@code ydsz_sys_dict_item} 表的分页查询条件。继承自 {@link PageQuery}，自带 {@code pageNum} /
 * {@code pageSize} / {@code orderBy} / {@code sort} 等通用分页参数。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 字典类型编码精确匹配（可选）
 *   <li>{@code itemCode} — 字典项编码模糊匹配（可选）
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
public class DictItemPageQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  private String typeCode;

  private String itemCode;

  private String status;
}
