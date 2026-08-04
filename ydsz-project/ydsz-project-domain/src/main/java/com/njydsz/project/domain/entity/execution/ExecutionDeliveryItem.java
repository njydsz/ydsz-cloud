package com.njydsz.project.domain.entity.execution;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交付物实体。
 *
 * <p>对应数据库表 {@code ydsz_execution_delivery_item}，记录项目履约过程中的交付物清单。
 * 每个交付物对应 WBS 任务（{@link ExecutionWbsTask}）的具体产出，支持验收管理。
 *
 * <p><b>交付物类型：</b>
 * <ul>
 *   <li>DOCUMENT：文档（方案/报告/手册）</li>
 *   <li>CODE：代码产物</li>
 *   <li>CONFIG：配置项</li>
 *   <li>HARDWARE：硬件设备</li>
 *   <li>SERVICE：服务交付</li>
 * </ul>
 *
 * <p><b>验收流程：</b>交付物创建 → 提交验收 → 客户确认 → 验收通过。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionWbsTask WBS 任务
 * @see ExecutionClosure 项目结项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_execution_delivery_item")
public class ExecutionDeliveryItem extends MpBaseEntity<String> {


}
