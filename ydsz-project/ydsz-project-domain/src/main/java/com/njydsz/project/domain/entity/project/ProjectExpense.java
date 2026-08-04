package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目费用实体。
 *
 * <p>对应数据库表 {@code ydsz_project_expense}，记录项目执行过程中产生的各类费用。
 * 费用按类别（人力/差旅/采购/外包/其他）归集，与预算明细（{@link ProjectBudgetItem}）关联。
 *
 * <p><b>核心流程：</b>
 * <ul>
 *   <li>费用报销申请 → 审批 → 财务入账 → 预算核销</li>
 *   <li>费用金额回写至 {@link ProjectBudgetItem} 实际使用统计</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectBudgetItem 立项预算明细
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_expense")
public class ProjectExpense extends MpBaseEntity<String> {


}
