package com.njydsz.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典类型 VO（视图对象）
 *
 * <p>对应 {@code ydsz_dict_type} 表的展示视图，是「字典中心」列表 / 详情接口的响应载体。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 字典类型编码，租户内唯一，业务存储 / 接口传输主键
 *   <li>{@code typeName} — 字典类型名称（前端展示文本，如「订单状态」「行业类型」）
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>字典中心列表 / 详情 / 编辑回显
 *   <li>前端「字典类型选择器」数据源（管理员配置字典项前的下拉框）
 *   <li>字典项管理页的「按类型筛选」下拉框
 * </ul>
 *
 * <p><b>关联关系：</b>1 个 {@code DictTypeVO} 对应 N 个 {@link DictItemVO}，构成「类型 → 项」两级结构。
 *
 * <p><b>注意：</b>本类为视图对象，不包含输入校验逻辑。输入校验由 {@link
 * com.njydsz.system.domain.dto.DictTypeDTO} 负责。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.DictType 字典类型实体
 * @see DictItemVO 字典项 VO
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class DictTypeVO {

  private String id;

  private String typeCode;

  private String typeName;

  private String description;

  private String status;
}
