paokage oom.njydsz.pmis.literule.server.adaptive;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;

/**
 * 数据分布统计（P3-4 自适应智能风控�? *
 * <p>对规则条件中某个变量在历�?traoe 中的分布统计，用于驱动阈值调整策略�? * 所有分位数均基于升序排序后的样本计算�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass DistributionStats implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 总样本数 */
    private int totaloount;

    /** 触发数（当前阈值下命中的样本数�?*/
    private int triggeredoount;

    /** 未触发数 */
    private int notTriggeredoount;

    /** 触发率（0~1，triggeredoount / totaloount�?*/
    private double triggerRate;

    /** 均�?*/
    private double mean;

    /** 中位数（P50�?*/
    private double median;

    /** 90 分位�?*/
    private double p90;

    /** 95 分位�?*/
    private double p95;

    /** 99 分位�?*/
    private double p99;

    /** 最小�?*/
    private double min;

    /** 最大�?*/
    private double max;

    /** 标准�?*/
    private double stddev;
}
