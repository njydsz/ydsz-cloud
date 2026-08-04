package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同主表实体。
 *
 * <p>对应数据库表 {@code ydsz_project_contract}，承载项目合同全生命周期数据。
 * 支持主合同、补充协议（{@link ProjectContractSupplement}）、变更单（{@link ProjectContractChange}），
 * 记录合同金额、税率、收款条件、履约期限等核心商务信息。
 *
 * <p><b>合同类型：</b>
 * <ul>
 *   <li><b>主合同</b>（{@code contractType=MAIN}）：初始签订的框架合同</li>
 *   <li><b>补充协议</b>（{@code contractType=SUPPLEMENT}）：对主合同的条款补充/修正</li>
 *   <li><b>变更单</b>（{@code contractType=CHANGE}）：对主合同的单项变更</li>
 * </ul>
 *
 * <p><b>关联关系：</b>
 * <ul>
 *   <li>合同 → 项目（{@code projectId}）：一个项目可关联多个合同</li>
 *   <li>合同 → 客户（{@code customerId}）：合同签约主体</li>
 *   <li>合同 → 立项（间接）：立项时可引用已有合同模板</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContractSupplement 补充协议
 * @see ProjectContractChange 合同变更单
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_contract")
public class ProjectContract extends MpBaseEntity<String> {


}
