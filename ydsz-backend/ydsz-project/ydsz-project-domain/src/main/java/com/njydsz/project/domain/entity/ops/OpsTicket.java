package com.njydsz.project.domain.entity.ops;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运维工单实体。
 *
 * <p>对应数据库表 {@code ydsz_ops_ticket}，记录项目交付后的运维服务工单。
 * 项目结项进入运维阶段后，客户通过工单系统提交运维需求。
 *
 * <p><b>工单类型：</b>
 * <ul>
 *   <li>INCIDENT：故障报修</li>
 *   <li>REQUEST：服务请求</li>
 *   <li>PROBLEM：问题报告</li>
 *   <li>CHANGE：变更申请</li>
 * </ul>
 *
 * <p><b>工单流程：</b>提交 → 分配 → 处理 → 验收 → 关闭。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_ops_ticket")
public class OpsTicket extends MpBaseEntity<String> {


}
