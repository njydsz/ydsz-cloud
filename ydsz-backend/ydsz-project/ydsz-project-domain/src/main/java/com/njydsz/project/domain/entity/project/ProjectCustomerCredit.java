package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户授信实体。
 *
 * <p>对应数据库表 {@code ydsz_project_customer_credit}，管理客户的信用额度与账期。
 * 授信信息用于合同签订时的商务决策，控制项目风险。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code customerId}：客户 ID</li>
 *   <li>{@code creditLimit}：授信总额度</li>
 *   <li>{@code usedAmount}：已用额度</li>
 *   <li>{@code paymentTerms}：账期条件</li>
 *   <li>{@code creditLevel}：信用等级（A/B/C/D）</li>
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
@TableName("ydsz_project_customer_credit")
public class ProjectCustomerCredit extends MpBaseEntity<String> {


}
