package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 立项预算明细实体。
 *
 * <p>对应数据库表 {@code ydsz_project_budget_item}，记录项目立项阶段的预算明细条目。
 * 每个立项（{@link ProjectInitiation}）可包含多条预算项，按类别（人力/差旅/采购）拆分。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code initiationId}：所属立项 ID</li>
 *   <li>{@code budgetCategory}：预算类别（HUMAN_RESOURCE / TRAVEL / PROCUREMENT / OTHER）</li>
 *   <li>{@code budgetAmount}：预算金额</li>
 *   <li>{@code plannedAmount}：计划使用金额</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_budget_item")
public class ProjectBudgetItem extends MpBaseEntity<String> {


}
