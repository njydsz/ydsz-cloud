package com.njydsz.pmis.project.vo.execution;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * EVM 挣值测量 VO（对外接口返回视图）
 *
 * <p>从 {@link com.njydsz.pmis.project.entity.EvmMeasureDO} 转换而来，
 * 剥离了敏感字段：{@code tenantId}、{@code providerTraceId}、{@code deleted}。
 *
 * <p>设计参考：{@code com.njydsz.pmis.userinfo.vo.UserVO} 的 DO/VO 分离模式。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvmMeasureVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目立项ID */
    private String initiationId;
    /** WBS 任务ID（可空：项目级度量） */
    private String wbsTaskId;
    /** 所属期间（YYYY-MM） */
    private String period;

    /** 计划值（Budgeted Cost of Work Scheduled） */
    private BigDecimal pv;
    /** 挣值（Budgeted Cost of Work Performed） */
    private BigDecimal ev;
    /** 实际成本（Actual Cost of Work Performed） */
    private BigDecimal ac;
    /** 完工预算（Budget at Completion） */
    private BigDecimal bac;

    /** 成本绩效指数 = EV/AC */
    private BigDecimal cpi;
    /** 进度绩效指数 = EV/PV */
    private BigDecimal spi;
    /** 成本偏差 = EV-AC */
    private BigDecimal cv;
    /** 进度偏差 = EV-PV */
    private BigDecimal sv;
    /** 完工估算 = BAC/CPI */
    private BigDecimal eac;
    /** 完工偏差 = BAC-EAC */
    private BigDecimal vac;
    /** 完工尚需 = EAC-AC */
    private BigDecimal etc;
    /** 完工绩效指数 = (BAC-EV)/(BAC-AC) */
    private BigDecimal tcpi;

    /** 预警等级：EvmAlertLevel.code */
    private String alertLevel;
    /** 预警原因 */
    private String alertReason;

    /** 度量日期 */
    private LocalDate measureDate;
    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
