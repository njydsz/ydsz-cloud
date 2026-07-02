package com.njydsz.pmis.execution.engine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预警编码生成器
 *
 * <p>格式：ALT-{TYPE}-{YYYYMMDD}-{HHMMss}-{4位随机}
 * <p>示例：ALT-BUDGET-20260701-153045-7821
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class AlertCodeGen {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

    private AlertCodeGen() {}

    /**
     * 生成下一个预警编码
     *
     * @param type  预警类型（如 BUDGET/EVM/MARGIN）；为空时使用 GEN
     * @param level 预警等级（如 YELLOW/RED）；为空时省略
     * @return 预警编码
     */
    public static String next(String type, String level) {
        LocalDateTime now = LocalDateTime.now();
        String typePart = type == null ? "GEN" : type.toUpperCase();
        String lvlPart = level == null ? "" : ("-" + level.toUpperCase());
        int rand = ThreadLocalRandom.current().nextInt(0, 10000);
        return String.format("ALT%s-%s-%s-%s-%04d",
                lvlPart, typePart, DATE_FMT.format(now), TIME_FMT.format(now), rand);
    }
}
