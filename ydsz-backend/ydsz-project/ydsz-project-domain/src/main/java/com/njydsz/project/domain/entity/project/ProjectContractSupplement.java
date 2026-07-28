package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同补充协议实体。
 *
 * <p>对应数据库表 {@code ydsz_project_contract_supplement}，记录主合同（{@link ProjectContract}）的补充协议。
 * 补充协议是对主合同条款的修正或补充，具有同等法律效力。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>合同金额调整（增补/减项）</li>
 *   <li>履约期限延长</li>
 *   <li>交付范围变更</li>
 *   <li>付款条件调整</li>
 * </ul>
 *
 * <p><b>关联关系：</b>每个补充协议关联一个主合同，通过 {@code contractId} 字段关联。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContract 合同主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_contract_supplement")
public class ProjectContractSupplement extends MpBaseEntity<String> {


}
