package com.njydsz.project.domain.entity.warranty;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质保记录实体。
 *
 * <p>对应数据库表 {@code ydsz_warranty}，记录项目交付后的质量保证期信息。
 * 质保期是合同约定的一部分，明确免费维护范围与期限。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code contractId}：关联合同</li>
 *   <li>{@code warrantyStart} / {@code warrantyEnd}：质保起止日期</li>
 *   <li>{@code warrantyScope}：质保范围说明</li>
 *   <li>{@code warrantyAmount}：质保金/保证金</li>
 *   <li>{@code status}：质保状态（ACTIVE / EXPIRED / TERMINATED）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContract 合同主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_warranty")
public class Warranty extends MpBaseEntity<String> {


}
