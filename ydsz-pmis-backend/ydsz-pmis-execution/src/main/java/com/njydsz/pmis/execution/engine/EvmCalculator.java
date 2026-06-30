package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.enums.EvmAlertLevel;

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

    /** 默认告警阈值 */
    public static final double DEFAULT_CPI_YELLOW = 0.95;
    public static final double DEFAULT_CPI_RED = 0.85;
    public static final double DEFAULT_SPI_YELLOW = 0.95;
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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

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
        public BigDecimal pv;
        public BigDecimal ev;
        public BigDecimal ac;
        public BigDecimal bac;
        public BigDecimal cv;
        public BigDecimal sv;
        public BigDecimal cpi;
        public BigDecimal spi;
        public BigDecimal eac;
        public BigDecimal vac;
        public BigDecimal etc;
        public BigDecimal tcpi;
        public EvmAlertLevel alertLevel;
        public String alertReason;
    }
}
