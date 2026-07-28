package com.njydsz.project.domain.entity.execution;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * WBS 任务实体。
 *
 * <p>对应数据库表 {@code ydsz_execution_wbs_task}，存储项目执行阶段的 WBS（Work Breakdown Structure，工作分解结构）任务。
 * WBS 任务是项目履约管理的核心载体，按层级拆分，支持进度填报与工时登记。
 *
 * <p><b>层级结构：</b>
 * <ul>
 *   <li>L1 项目阶段（如设计、开发、测试、上线）</li>
 *   <li>L2 工作包（如前端开发、后端开发、接口联调）</li>
 *   <li>L3 任务单元（具体可分配的最小工作单元）</li>
 * </ul>
 *
 * <p><b>关联关系：</b>
 * <ul>
 *   <li>工时条目（{@link ExecutionTimeEntry}）：任务工时填报</li>
 *   <li>交付物（{@link ExecutionDeliveryItem}）：任务交付物关联</li>
 *   <li>风险（{@link ExecutionRisk}）：任务级风险</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionTimeEntry 工时条目
 * @see ExecutionDeliveryItem 交付物
 * @see ExecutionRisk 执行风险
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_execution_wbs_task")
public class ExecutionWbsTask extends MpBaseEntity<String> {


}
