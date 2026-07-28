package com.njydsz.project.domain.entity.execution;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时录入实体。
 *
 * <p>对应数据库表 {@code ydsz_execution_time_entry}，记录项目成员填报的工时数据。
 * 工时按日期和 WBS 任务（{@link ExecutionWbsTask}）归集，是项目履约管理和成本核算的基础数据。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code entryDate}：工时归属日期</li>
 *   <li>{@code hours}：填报工时（小时）</li>
 *   <li>{@code wbsTaskId}：所属 WBS 任务</li>
 *   <li>{@code userId}：填报人</li>
 *   <li>{@code description}：工作内容描述</li>
 *   <li>{@code billable}：是否可计费</li>
 * </ul>
 *
 * <p><b>业务约束：</b>同一用户同一天同一 WBS 任务不可重复填报。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionWbsTask WBS 任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_execution_time_entry")
public class ExecutionTimeEntry extends MpBaseEntity<String> {


}
