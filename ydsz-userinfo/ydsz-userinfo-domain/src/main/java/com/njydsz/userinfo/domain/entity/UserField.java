package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户自定义字段实体
 *
 * <p>对应数据库表 {@code ydsz_user_field}，支持在不修改表结构的前提下为用户动态扩展属性。
 * 用于存储各业务线个性化字段（如「员工编号」「工号」「入职日期」「紧急联系人」等），
 * 避免业务方在 {@link UserAccount} 上堆叠固定字段。
 *
 * <p><b>设计模式：</b>EAV（Entity-Attribute-Value，实体-属性-值）模式。
 * <ul>
 *   <li>Entity = {@link UserAccount#getId()}</li>
 *   <li>Attribute = {@link #fieldKey}</li>
 *   <li>Value = {@link #fieldValue}</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 查询用户某个自定义字段
 * UserField empNo = userFieldMapper.selectOne(
 *     new LambdaQueryWrapper<UserField>()
 *         .eq(UserField::getUserId, userId)
 *         .eq(UserField::getFieldKey, "employee_no")
 * );
 * String employeeNo = empNo != null ? empNo.getFieldValue() : null;
 *
 * // 查询用户所有扩展字段（Map<key, value>）
 * List<UserField> fields = userFieldMapper.selectList(
 *     new LambdaQueryWrapper<UserField>().eq(UserField::getUserId, userId));
 * Map<String, String> ext = fields.stream()
 *     .collect(Collectors.toMap(UserField::getFieldKey, UserField::getFieldValue));
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>{@code fieldValue} 全部以 String 存储，数值/日期类型由业务层解析</li>
 *   <li>高频查询字段建议通过 {@code fieldKey} 加普通索引</li>
 *   <li>避免大量扩展字段影响查询性能（建议单用户 &lt; 20 个）</li>
 * </ul>
 *
 * <p><b>索引设计：</b>普通索引 {@code idx_user_id}（{@code user_id}）、
 * {@code idx_field_key}（{@code field_key}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserAccount 用户实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_user_field")
public class UserField extends MpBaseEntity<String> {

    /** 用户 ID，关联 {@link UserAccount#getId()} */
    private String userId;

    /**
     * 字段键名。
     *
     * <p>建议使用 snake_case 命名（如 {@code employee_no}/{@code hire_date}），
     * 全局唯一建议格式 {@code <module>_<field>}。
     */
    private String fieldKey;

    /**
     * 字段值。
     *
     * <p>统一以 String 存储，类型语义由 {@link #fieldType} 描述。
     */
    private String fieldValue;

    /**
     * 字段类型。
     *
     * <p>取值：{@code STRING} / {@code NUMBER} / {@code DATE} / {@code BOOLEAN}。
     * 仅作语义标识，存储层不强制类型校验。
     */
    private String fieldType;
}
