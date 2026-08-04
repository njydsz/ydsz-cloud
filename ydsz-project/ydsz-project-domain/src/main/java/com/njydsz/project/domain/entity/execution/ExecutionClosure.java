package com.njydsz.project.domain.entity.execution;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目结项实体。
 *
 * <p>对应数据库表 {@code ydsz_execution_closure}，记录项目结项/关闭的相关数据。
 * 项目进入结项阶段后，完成交付物验收、费用结算、经验总结等收尾工作。
 *
 * <p><b>结项流程：</b>
 * <ul>
 *   <li>发起结项申请 → 交付物验收 → 财务结算 → 结项审批 → 项目关闭</li>
 *   <li>结项报告含项目绩效分析、经验教训、后续运维交接</li>
 * </ul>
 *
 * <p><b>关联关系：</b>
 * <ul>
 *   <li>关联 {@link ProjectInitiation}（一个项目对应一条结项记录）</li>
 *   <li>关联 {@link ExecutionDeliveryItem}（验收交付物清单）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项
 * @see ExecutionDeliveryItem 交付物
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_execution_closure")
public class ExecutionClosure extends MpBaseEntity<String> {


}
