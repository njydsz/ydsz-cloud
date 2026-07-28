package com.njydsz.project.domain.entity.execution;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 执行风险实体。
 *
 * <p>对应数据库表 {@code ydsz_execution_risk}，记录项目执行过程中识别的风险项。
 * 风险按等级（高/中/低）和状态（识别/评估/应对/关闭）进行全生命周期管理。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code wbsTaskId}：关联 WBS 任务（可选）</li>
 *   <li>{@code riskType}：风险类型（技术/进度/成本/资源/商务）</li>
 *   <li>{@code riskLevel}：风险等级（HIGH / MEDIUM / LOW）</li>
 *   <li>{@code probability} / {@code impact}：概率与影响评估</li>
 *   <li>{@code responseStrategy}：应对策略（规避/减轻/转移/接受）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionWbsTask WBS 任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_execution_risk")
public class ExecutionRisk extends MpBaseEntity<String> {


}
