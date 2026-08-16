package com.njydsz.system.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.baomidou.mybatisplus.annotation.TableName;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 字典项实体
 *
 * <p>对应数据库表 {@code ydsz_dict_item}，字段与 DDL 完全对齐。 字典项是字典类型的具体枚举值，每条记录属于某个 {@link DictType}。
 *
 * <p><b>字段说明：</b>
 *
 * <ul>
 *   <li>{@code typeCode}：所属字典类型编码（逻辑外键，对应 {@code ydsz_dict_type.type_code}）
 *   <li>{@code itemCode}：字典项编码（同 typeCode 内唯一）
 *   <li>{@code itemValue}：字典项真实值（业务代码引用的值，如 {@code "PAID"}）
 *   <li>{@code sortOrder}：展示排序序号
 *   <li>{@code parentId}：父级字典项 ID，支持树形字典（如行政区划）
 *   <li>{@code extJson}：扩展属性 JSONB 字符串，承载自定义属性（如色值、图标）
 * </ul>
 *
 * <p><b>典型使用：</b>前端下拉框从 {@code /api/v1/dict/item/list?typeCode=order_status} 拉取， 显示 {@code
 * itemCode}（如「已支付」），提交时传 {@code itemValue}（如 {@code "PAID"}）。
 *
 * <p><b>索引设计：</b>索引 {@code idx_type_code}（{@code type_code}）加速按类型查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DictType 字典类型实体（字典两级体系上层）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_item")
public class DictItem extends MpBaseEntity<String> {

  /** 所属字典类型编码（逻辑外键 → {@code ydsz_dict_type.type_code}） */
  private String typeCode;

  /** 字典项编码（同 typeCode 内唯一） */
  private String itemCode;

  /** 字典项真实值（业务代码引用的枚举值，如 "PAID"） */
  private String itemValue;

  /** 展示排序序号（升序） */
  private Integer sortOrder;

  /** 父级字典项 ID，支持树形字典（如行政区划、组织架构） */
  private String parentId;

  /** 字典项描述 */
  private String description;

  /** 扩展属性 JSONB 字符串，承载自定义属性（如色值、图标、URL 等） */
  private String extJson;
}
