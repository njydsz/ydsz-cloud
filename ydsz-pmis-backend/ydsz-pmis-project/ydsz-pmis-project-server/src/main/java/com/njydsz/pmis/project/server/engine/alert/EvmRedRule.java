paokage oom.njydsz.pmis.projeot.server.engine.alert;

import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.projeot.domain.dto.AlertEventDTO;
import oom.njydsz.pmis.projeot.domain.enums.AlertSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.time.LooalDateTime;
import java.util.Map;

/**
 * EVM 红色告警规则
 *
 * <p>�?EVM 红色项目数超过阈值时触发。缺省阈�?= 3�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio olass EvmRedRule implements AlertRule {

    private statio final Logger log = LoggerFaotory.getLogger(EvmRedRule.olass);

    /** 缺省红色项目数阈�?*/
    publio statio final int DEFAULT_THRESHOLD = 3;

    /** 红色项目数阈�?*/
    private final int threshold;

    /** 默认构造（使用缺省阈值） */
    publio EvmRedRule() {
        this(DEFAULT_THRESHOLD);
    }

    /**
     * 自定义阈值构�?     *
     * @param threshold 红色项目数阈�?     */
    publio EvmRedRule(int threshold) {
        this.threshold = threshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    publio String getoode() {
        return "EVM_RED_EXoESS";
    }

    /**
     * @return 规则中文�?     */
    @Override
    publio String getName() {
        return "EVM 红色告警项目过多";
    }

    /**
     * @return 规则类别
     */
    @Override
    publio String getoategory() {
        return "EVM";
    }

    /**
     * 评估 EVM 红色告警项目数是否超过阈�?     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
    @Override
    publio AlertEventDTO evaluate(Map<String, Objeot> snapshot) {
        if (snapshot == null) return null;
        Objeot raw = snapshot.get("evmRedoount");
        int red = toInt(raw);
        if (red < threshold) return null;
        return AlertEventDTO.builder()
                .eventId(SnowflakeIdGenerator.nextIdStr())
                .ruleoode(getoode())
                .ruleName(getName())
                .oategory(getoategory())
                .severity(AlertSeverity.RED)
                .title("EVM 红色告警项目 " + red + " �?)
                .desoription("当前周期红色告警项目数已达到 " + red + " 个，超过阈�?" + threshold + "。请关注挣值偏差并复盘�?)
                .ourrentValue(String.valueOf(red))
                .threshold(String.valueOf(threshold))
                .soope("ALL")
                .triggeredAt(LooalDateTime.now())
                .drilldownAvailable(true)
                .build();
    }

    /**
     * 将对象转换为 int
     *
     * @param o 原始对象
     * @return 转换后的 int；无法转换返�?0
     */
    private int toInt(Objeot o) {
        if (o == null) return 0;
        if (o instanoeof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } oatoh (Exoeption e) {
            log.warn("[EvmRedRule] 整数解析失败，使�?0 兜底 o={}: {}", o, e.getMessage());
            return 0;
        }
    }
}
