package com.njydsz.pmis.finance.server.engine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 预警编码生成器
 *
 * <p>格式：ALT-{TYPE}-{YYYYMMDD}-{HHMMssSSS}-{4位随机}-{2位序列}
 * <p>示例：ALT-BUDGET-20260701-153045782-7821-01
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class AlertCodeGen {

    /** 日期格式化器（yyyyMMdd） */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 时间格式化器（HHmmssSSS，毫秒精度避免同秒碰撞） */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmssSSS");
    /** 自增序列号（0-99 循环），进一步提升同毫秒内唯一性 */
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /** 私有构造，工具类不可实例化 */
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
        int seq = SEQ.getAndUpdate(i -> (i + 1) % 100);
        return String.format("ALT%s-%s-%s-%s-%04d-%02d",
                lvlPart, typePart, DATE_FMT.format(now), TIME_FMT.format(now), rand, seq);
    }
}
