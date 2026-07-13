package com.njydsz.pmis.project.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.njydsz.pmis.literule.server.spi.BudgetSnapshotProvider.BudgetSnapshot;

import lombok.Data;

/**
 * 预算快照 VO（对外接口返回视图）
 *
 * <p>从 SPI 层 {@link BudgetSnapshot} 转换而来，
 * 剥离 SPI 接口细节，避免 literule 模块的 record 类型直接暴露给前端。
 *
 * <p>设计参考：{@code com.njydsz.pmis.project.domain.vo.RiskVO} 的 DO/VO 分离模式。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BudgetSnapshotVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 预算总额 */
    private BigDecimal totalBudget;

    /** 已发生成本（采购+费用+分摊） */
    private BigDecimal incurredCost;

    /** 预算使用率（0~1+） */
    private double usageRatio;

    /**
     * 从 SPI 层 BudgetSnapshot 转换为 VO
     *
     * @param snapshot SPI 层快照
     * @return VO 视图；入参为 null 返回 null
     */
    public static BudgetSnapshotVO from(BudgetSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        BudgetSnapshotVO vo = new BudgetSnapshotVO();
        vo.setProjectId(snapshot.projectId());
        vo.setProjectName(snapshot.projectName());
        vo.setTotalBudget(snapshot.totalBudget());
        vo.setIncurredCost(snapshot.incurredCost());
        vo.setUsageRatio(snapshot.usageRatio());
        return vo;
    }
}
