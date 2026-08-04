package com.njydsz.project.domain.entity.evm;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVM 挣值测量实体。
 *
 * <p>对应数据库表 {@code ydsz_evm_measure}，存储项目挣值管理（Earned Value Management）的测量数据。
 * EVM 是项目管理"金三角"（范围-进度-成本）偏离分析的核心工具。
 *
 * <p><b>核心指标：</b>
 * <ul>
 *   <li>{@code pv}：计划价值（Planned Value）</li>
 *   <li>{@code ev}：挣值（Earned Value）</li>
 *   <li>{@code ac}：实际成本（Actual Cost）</li>
 *   <li>{@code sv}：进度偏差（Schedule Variance = EV - PV）</li>
 *   <li>{@code cv}：成本偏差（Cost Variance = EV - AC）</li>
 *   <li>{@code spi}：进度绩效指数（SPI = EV / PV）</li>
 *   <li>{@code cpi}：成本绩效指数（CPI = EV / AC）</li>
 *   <li>{@code bac}：完工预算（Budget at Completion）</li>
 *   <li>{@code eac}：完工估算（Estimate at Completion）</li>
 * </ul>
 *
 * <p><b>测量周期：</b>通常按月度或双周采集，支持趋势分析。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_evm_measure")
public class EvmMeasure extends MpBaseEntity<String> {


}
