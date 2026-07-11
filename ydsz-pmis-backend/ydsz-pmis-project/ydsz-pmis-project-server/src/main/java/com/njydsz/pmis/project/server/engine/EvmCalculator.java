package com.njydsz.pmis.project.server.engine;

import com.njydsz.pmis.project.domain.enums.EvmAlertLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * EVM 挣值计算引擎
 *
 * <p>核心公式：
 * <ul>
 *   <li>CV = EV - AC（成本偏差）</li>
 *   <li>SV = EV - PV（进度偏差）</li>
 *   <li>CPI = EV / AC（成本绩效指数；>1 节约，<1 超支）</li>
 *   <li>SPI = EV / PV（进度绩效指数；>1 提前，<1 滞后）</li>
 *   <li>EAC = BAC / CPI（完工估算）</li>
 *   <li>VAC = BAC - EAC（完工偏差）</li>
 *   <li>ETC = EAC - AC（完工尚需）</li>
 *   <li>TCPI = (BAC - EV) / (BAC - AC)（完工绩效指数）</li>
 * </ul>
 *
 * <p>告警规则：CPI/SPI 任一跌破 red 阈值 → RED；任一跌破 yellow 阈值 → YELLOW。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class EvmCalculator {

    /** CPI 黄色阈值 */
    public static final double DEFAULT_CPI_YELLOW = 0.95;
    /** CPI 红色阈值 */
    public static final double DEFAULT_CPI_RED = 0.85;
    /** SPI 黄色阈值 */
    public static final double DEFAULT_SPI_YELLOW = 0.95;
    /** SPI 红色阈值 */
    public static final double DEFAULT_SPI_RED = 0.85;

    /**
     * 计算完整 EVM 指标
     *
     * @param pv  计划值
     * @param ev  挣值
     * @param ac  实际成本
     * @param bac 完工预算
     * @return EVMResult 包含 CV/SV/CPI/SPI/EAC/VAC/ETC/TCPI/alertLevel
     */
    public static EVMResult calculate(BigDecimal pv, BigDecimal ev, BigDecimal ac, BigDecimal bac) {
        return calculate(pv, ev, ac, bac,
                DEFAULT_CPI_YELLOW, DEFAULT_CPI_RED, DEFAULT_SPI_YELLOW, DEFAULT_SPI_RED);
    }

    /**
     * 计算完整 EVM 指标（自定义阈值）
     *
     * @param pv        计划值
     * @param ev        挣值
     * @param ac        实际成本
     * @param bac       完工预算
     * @param cpiYellow CPI 黄色阈值
     * @param cpiRed    CPI 红色阈值
     * @param spiYellow SPI 黄色阈值
     * @param spiRed    SPI 红色阈值
     * @return EVMResult 包含 CV/SV/CPI/SPI/EAC/VAC/ETC/TCPI/alertLevel
     */
    public static EVMResult calculate(BigDecimal pv, BigDecimal ev, BigDecimal ac, BigDecimal bac,
                                      double cpiYellow, double cpiRed,
                                      double spiYellow, double spiRed) {
        BigDecimal pvN = nz(pv);
        BigDecimal evN = nz(ev);
        BigDecimal acN = nz(ac);
        BigDecimal bacN = nz(bac);

        // CV / SV
        BigDecimal cv = evN.subtract(acN);
        BigDecimal sv = evN.subtract(pvN);

        // CPI / SPI（避免除零，使用 1.0 兜底）
        double cpi = acN.signum() == 0 ? 1.0 : evN.divide(acN, 4, RoundingMode.HALF_UP).doubleValue();
        double spi = pvN.signum() == 0 ? 1.0 : evN.divide(pvN, 4, RoundingMode.HALF_UP).doubleValue();

        // EAC = BAC / CPI
        BigDecimal eac = cpi == 0
                ? bacN
                : BigDecimal.valueOf(bacN.doubleValue() / cpi).setScale(2, RoundingMode.HALF_UP);
        // VAC = BAC - EAC
        BigDecimal vac = bacN.subtract(eac);
        // ETC = EAC - AC
        BigDecimal etc = eac.subtract(acN);
        // TCPI = (BAC - EV) / (BAC - AC)；分母为 0 时返回 1
        BigDecimal bacMinusAc = bacN.subtract(acN);
        double tcpi;
        if (bacMinusAc.signum() == 0) {
            tcpi = 1.0;
        } else {
            tcpi = bacN.subtract(evN).divide(bacMinusAc, 4, RoundingMode.HALF_UP).doubleValue();
        }

        // 告警
        EvmAlertLevel level = EvmAlertLevel.evaluate(cpi, spi, cpiYellow, cpiRed, spiYellow, spiRed);
        String reason = buildReason(cpi, spi, cpiYellow, cpiRed, spiYellow, spiRed);

        EVMResult r = new EVMResult();
        r.pv = pvN;
        r.ev = evN;
        r.ac = acN;
        r.bac = bacN;
        r.cv = cv;
        r.sv = sv;
        r.cpi = BigDecimal.valueOf(cpi).setScale(4, RoundingMode.HALF_UP);
        r.spi = BigDecimal.valueOf(spi).setScale(4, RoundingMode.HALF_UP);
        r.eac = eac;
        r.vac = vac;
        r.etc = etc;
        r.tcpi = BigDecimal.valueOf(tcpi).setScale(4, RoundingMode.HALF_UP);
        r.alertLevel = level;
        r.alertReason = reason;
        r.forecastCompletionDate = forecastCompletionDate(spi, pvN, evN);
        r.recommendedAction = recommendedAction(level, cpi, spi);
        return r;
    }

    /**
     * 空值转零
     *
     * @param v 原始值
     * @return 非空原值；null 返回 ZERO
     */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 构建告警原因描述
     *
     * @param cpi  成本绩效指数
     * @param spi  进度绩效指数
     * @param cpiY CPI 黄色阈值
     * @param cpiR CPI 红色阈值
     * @param spiY SPI 黄色阈值
     * @param spiR SPI 红色阈值
     * @return 告警原因字符串；无告警返回 null
     */
    private static String buildReason(double cpi, double spi,
                                      double cpiY, double cpiR,
                                      double spiY, double spiR) {
        if (cpi < cpiR) return "CPI=" + String.format("%.2f", cpi) + " 跌破红色阈值 " + cpiR;
        if (spi < spiR) return "SPI=" + String.format("%.2f", spi) + " 跌破红色阈值 " + spiR;
        if (cpi < cpiY) return "CPI=" + String.format("%.2f", cpi) + " 跌破黄色阈值 " + cpiY;
        if (spi < spiY) return "SPI=" + String.format("%.2f", spi) + " 跌破黄色阈值 " + spiY;
        return null;
    }

    /**
     * 基于SPI预测完工日期。
     * <p>公式：预计完工日期 = 当前日期 + (剩余工期 / SPI)
     * <p>SPI < 1 时完工日期将延后，SPI > 1 时可能提前。
     *
     * @param spi        进度绩效指数
     * @param pv         计划值
     * @param ev         挣值
     * @return 预测完工日期；无法预测返回 null
     */
    private static LocalDate forecastCompletionDate(double spi, BigDecimal pv, BigDecimal ev) {
        if (spi <= 0 || spi >= 1.0) {
            // SPI≥1 说明进度正常或提前，不需要预警
            return null;
        }
        // 剩余工作量 = PV - EV
        BigDecimal remaining = pv.subtract(ev);
        if (remaining.signum() <= 0) {
            return null;
        }
        // 按当前进度，剩余工作需要的时间 = 剩余量 / (总量/已用时间) / SPI
        // 简化模型：剩余天数 ≈ 剩余工作量占比 * 总工期 / SPI
        // 这里仅提供方向性预测，实际需结合项目日历
        long estimatedDelayDays = Math.round(remaining.divide(pv, 4, RoundingMode.HALF_UP).doubleValue() / spi * 30);
        return LocalDate.now().plusDays(estimatedDelayDays);
    }

    /**
     * 根据EVM指标生成推荐操作。
     *
     * @param level 告警级别
     * @param cpi   成本绩效指数
     * @param spi   进度绩效指数
     * @return 推荐操作描述
     */
    private static String recommendedAction(EvmAlertLevel level, double cpi, double spi) {
        if (level == EvmAlertLevel.NORMAL) {
            return "项目运行正常，继续保持";
        }
        StringBuilder sb = new StringBuilder();
        if (cpi < 0.85) {
            sb.append("成本严重超支，建议立即启动成本审查并调整预算；");
        } else if (cpi < 0.95) {
            sb.append("成本略有超支，建议关注成本趋势并优化资源配置；");
        }
        if (spi < 0.85) {
            sb.append("进度严重滞后，建议增加人力资源或调整交付范围；");
        } else if (spi < 0.95) {
            sb.append("进度略有滞后，建议加快关键路径任务执行；");
        }
        if (sb.length() == 0) {
            sb.append("建议持续监控");
        }
        return sb.toString();
    }

    /**
     * EVM 指标结果集
     */
    public static class EVMResult {
        /** 计划值 */
        public BigDecimal pv;
        /** 挣值 */
        public BigDecimal ev;
        /** 实际成本 */
        public BigDecimal ac;
        /** 完工预算 */
        public BigDecimal bac;
        /** 成本偏差 */
        public BigDecimal cv;
        /** 进度偏差 */
        public BigDecimal sv;
        /** 成本绩效指数 */
        public BigDecimal cpi;
        /** 进度绩效指数 */
        public BigDecimal spi;
        /** 完工估算 */
        public BigDecimal eac;
        /** 完工偏差 */
        public BigDecimal vac;
        /** 完工尚需 */
        public BigDecimal etc;
        /** 完工绩效指数 */
        public BigDecimal tcpi;
        /** 告警级别 */
        public EvmAlertLevel alertLevel;
        /** 告警原因 */
        public String alertReason;
        /** 预测完工日期（SPI<1时预警） */
        public LocalDate forecastCompletionDate;
        /** 推荐操作 */
        public String recommendedAction;
    }
}
