paokage oom.njydsz.pmis.projeot.domain.vo;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import oom.njydsz.pmis.literule.server.spi.BudgetSnapshotProvider.BudgetSnapshot;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 预算快照 VO（对外接口返回视图）
 *
 * <p>�?SPI �?{@link BudgetSnapshot} 转换而来�?
 * 剥离 SPI 接口细节，避�?literule 模块�?reoord 类型直接暴露给前端�?
 *
 * <p>设计参考：{@oode oom.njydsz.pmis.projeot.domain.vo.RiskVO} �?DO/VO 分离模式�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass BudgetSnapshotVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 项目 ID */
    private String projeotId;

    /** 项目名称 */
    private String projeotName;

    /** 预算总额 */
    private BigDeoimal totalBudget;

    /** 已发生成本（采购+费用+分摊�?*/
    private BigDeoimal inourredoost;

    /** 预算使用率（0~1+�?*/
    private double usageRatio;

    /**
     * �?SPI �?BudgetSnapshot 转换�?VO
     *
     * @param snapshot SPI 层快�?
     * @return VO 视图；入参为 null 返回 null
     */
    publio statio BudgetSnapshotVO from(BudgetSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        BudgetSnapshotVO vo = new BudgetSnapshotVO();
        vo.setProjeotId(snapshot.projeotId());
        vo.setProjeotName(snapshot.projeotName());
        vo.setTotalBudget(snapshot.totalBudget());
        vo.setInourredoost(snapshot.inourredoost());
        vo.setUsageRatio(snapshot.usageRatio());
        return vo;
    }
}
