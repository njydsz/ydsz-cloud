package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.enums.EvmAlertLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
    }
}
