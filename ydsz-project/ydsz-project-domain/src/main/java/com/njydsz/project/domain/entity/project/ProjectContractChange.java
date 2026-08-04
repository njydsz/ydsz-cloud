package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同变更单实体。
 *
 * <p>对应数据库表 {@code ydsz_project_contract_change}，记录合同执行过程中的变更事项。
 * 变更单作为合同的附件，承载金额调整、条款变更、工期变更等内容。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *   <li>{@code contractId}：所属合同 ID</li>
 *   <li>{@code changeType}：变更类型（AMOUNT / TERM / SCOPE / OTHER）</li>
 *   <li>{@code changeAmount}：变更金额（正数=增加，负数=减少）</li>
 *   <li>{@code status}：审批状态（DRAFT / PENDING / APPROVED / REJECTED）</li>
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
@TableName("ydsz_project_contract_change")
public class ProjectContractChange extends MpBaseEntity<String> {


}
