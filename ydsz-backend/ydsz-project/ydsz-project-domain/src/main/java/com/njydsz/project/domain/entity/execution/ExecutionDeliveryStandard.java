package com.njydsz.project.domain.entity.execution;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交付标准实体。
 *
 * <p>对应数据库表 {@code ydsz_execution_delivery_standard}，定义项目交付物的验收标准。
 * 交付标准作为交付物（{@link ExecutionDeliveryItem}）的验收依据，确保交付质量。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *   <li>{@code standardCode}：标准编码</li>
 *   <li>{@code standardName}：标准名称</li>
 *   <li>{@code standardType}：标准类型（定性/定量/合规）</li>
 *   <li>{@code acceptanceCriteria}：验收条件描述</li>
 *   <li>{@code isMandatory}：是否强制要求</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionDeliveryItem 交付物
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_execution_delivery_standard")
public class ExecutionDeliveryStandard extends MpBaseEntity<String> {


}
