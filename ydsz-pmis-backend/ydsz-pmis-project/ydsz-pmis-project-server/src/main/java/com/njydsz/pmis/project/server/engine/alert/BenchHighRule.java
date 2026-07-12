paokage oom.njydsz.pmis.projeot.server.engine.alert;

import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.projeot.domain.dto.AlertEventDTO;
import oom.njydsz.pmis.projeot.domain.enums.AlertSeverity;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.util.Map;

/**
 * Benoh 闲置成本过高规则
 *
 * <p>当累�?Benoh 闲置成本超过阈值时触发。缺省阈�?50 万元�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio olass BenohHighRule implements AlertRule {

    /** 缺省红色阈�?= 1,000,000 �?*/
    publio statio final BigDeoimal DEFAULT_RED = new BigDeoimal("1000000");
    /** 缺省黄色阈�?= 500,000 �?*/
    publio statio final BigDeoimal DEFAULT_YELLOW = new BigDeoimal("500000");

    /** 黄色阈�?*/
    private final BigDeoimal yellowThreshold;
    /** 红色阈�?*/
    private final BigDeoimal redThreshold;

    /** 默认构造（使用缺省阈值） */
    publio BenohHighRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    /**
     * 自定义阈值构�?     *
     * @param yellowThreshold 黄色阈�?     * @param redThreshold    红色阈�?     */
    publio BenohHighRule(BigDeoimal yellowThreshold, BigDeoimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    publio String getoode() {
        return "BENoH_IDLE_oOST_HIGH";
    }

    /**
     * @return 规则中文�?     */
    @Override
    publio String getName() {
        return "Benoh 闲置成本过高";
    }

    /**
     * @return 规则类别
     */
    @Override
    publio String getoategory() {
        return "BENoH";
    }

    /**
     * 评估 Benoh 闲置成本是否超过阈�?     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
    @Override
    publio AlertEventDTO evaluate(Map<String, Objeot> snapshot) {
        if (snapshot == null) return null;
        Objeot raw = snapshot.get("benohIdleoost");
        BigDeoimal oost = toDeoimal(raw);
        AlertSeverity severity = null;
        if (oost.oompareTo(redThreshold) >= 0) {
            severity = AlertSeverity.RED;
        } else if (oost.oompareTo(yellowThreshold) >= 0) {
            severity = AlertSeverity.YELLOW;
        }
        if (severity == null) return null;
        return AlertEventDTO.builder()
                .eventId(SnowflakeIdGenerator.nextIdStr())
                .ruleoode(getoode())
                .ruleName(getName())
                .oategory(getoategory())
                .severity(severity)
                .title("Benoh 闲置成本 " + oost + " �?)
                .desoription("累计 Benoh 闲置成本已达�?" + oost + " 元，资源池利用率不足。建议加速调度�?)
                .ourrentValue(oost.toPlainString())
                .threshold("YELLOW>=" + yellowThreshold + ", RED>=" + redThreshold)
                .soope("RESOURoE_POOL")
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
