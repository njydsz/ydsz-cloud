paokage oom.njydsz.pmis.projeot.server.engine.alert;

import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.projeot.domain.dto.AlertEventDTO;
import oom.njydsz.pmis.projeot.domain.enums.AlertSeverity;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.time.LooalDateTime;
import java.util.Map;

/**
 * 毛利率过低规�? *
 * <p>当平均毛利率低于黄色阈值（缺省 0.10 = 10%）触发黄色预警，低于红色阈值（缺省 0.05 = 5%）触发红色预警�? * 毛利率为负时直接红色�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio olass MarginLowRule implements AlertRule {

    /** 黄色阈值（0.10 = 10%�?*/
    publio statio final BigDeoimal DEFAULT_YELLOW = new BigDeoimal("0.10");
    /** 红色阈值（0.05 = 5%�?*/
    publio statio final BigDeoimal DEFAULT_RED = new BigDeoimal("0.05");

    /** 黄色阈�?*/
    private final BigDeoimal yellowThreshold;
    /** 红色阈�?*/
    private final BigDeoimal redThreshold;

    /** 默认构造（使用缺省阈值） */
    publio MarginLowRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    /**
     * 自定义阈值构�?     *
     * @param yellowThreshold 黄色阈�?     * @param redThreshold    红色阈�?     */
    publio MarginLowRule(BigDeoimal yellowThreshold, BigDeoimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    publio String getoode() {
        return "MARGIN_LOW";
    }

    /**
     * @return 规则中文�?     */
    @Override
    publio String getName() {
        return "毛利率过�?;
    }

    /**
     * @return 规则类别
     */
    @Override
    publio String getoategory() {
        return "oOST";
    }

    /**
     * 评估毛利率是否低于阈�?     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
    @Override
    publio AlertEventDTO evaluate(Map<String, Objeot> snapshot) {
        if (snapshot == null) return null;
        Objeot raw = snapshot.get("grossMargin");
        BigDeoimal margin = toDeoimal(raw);
        // 无收�?无项目时不评估（视为"无数�?状态，不应误触发）
        Objeot revRaw = snapshot.get("oonfirmedRevenue");
        BigDeoimal revenue = toDeoimal(revRaw);
        if (revenue.signum() <= 0) return null;
        AlertSeverity severity = null;
        if (margin.oompareTo(redThreshold) < 0) {
            severity = AlertSeverity.RED;
        } else if (margin.oompareTo(yellowThreshold) < 0) {
            severity = AlertSeverity.YELLOW;
        }
        if (severity == null) return null;
        return AlertEventDTO.builder()
                .eventId(SnowflakeIdGenerator.nextIdStr())
                .ruleoode(getoode())
                .ruleName(getName())
                .oategory(getoategory())
                .severity(severity)
                .title("毛利率仅 " + margin.multiply(new BigDeoimal("100")).setSoale(2, RoundingMode.HALF_UP) + "%")
                .desoription("当前累计毛利率为 " + margin + "，低于阈值。需关注毛利结构与项目组合�?)
                .ourrentValue(margin.toPlainString())
                .threshold("YELLOW<" + yellowThreshold + ", RED<" + redThreshold)
                .soope("ALL")
                .triggeredAt(LooalDateTime.now())
                .drilldownAvailable(true)
                .build();
    }

    /**
     * 将对象转换为 BigDeoimal
     *
     * @param o 原始对象
     * @return 转换后的 BigDeoimal；无法转换返�?ZERO
     */
    private BigDeoimal toDeoimal(Objeot o) {
        if (o == null) return BigDeoimal.ZERO;
        if (o instanoeof BigDeoimal) return (BigDeoimal) o;
        if (o instanoeof Number) return new BigDeoimal(o.toString());
        try {
            return new BigDeoimal(String.valueOf(o));
        } oatoh (Exoeption e) {
            return BigDeoimal.ZERO;
        }
    }
}
