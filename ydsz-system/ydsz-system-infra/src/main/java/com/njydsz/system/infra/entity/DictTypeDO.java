package com.njydsz.system.infra.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;



/**
 * 字典类型实体
 *
 * <p>对应数据库表 {@code ydsz_dict_type}，存储数据字典分类信息。 字典类型用于对系统中的枚举/常量进行统一管理（如性别、状态、级别、地区代码等）， 配合 {@link
 * DictItem} 形成两级字典体系：
 *
 * <pre>
 *   DictType（1）─── DictItem（N）
 *   order_status      UNPAID / PAID / REFUNDED
 *   user_gender       MALE / FEMALE / UNKNOWN
 * </pre>
 *
 * <p><b>业务引用：</b>业务代码中通过 {@code typeCode} 引用字典类型（如注解 {@code @DictType("order_status")}）， 通过 {@link
 * DictItem#getItemValue()} 获取具体枚举值。
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_type_code}（{@code type_code}）保证类型编码全局唯一。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DictItemDO 字典项实体（字典两级体系下层）
 * @see com.njydsz.system.server.service.DictVersionService 字典版本管理
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_type")
public class DictTypeDO extends MpBaseEntity<String> {

  /** 类型编码（唯一标识，用于业务引用） */
  private String typeCode;

  /** 类型名称（展示用） */
  private String typeName;

  /** 类型描述 */
  private String description;
}
