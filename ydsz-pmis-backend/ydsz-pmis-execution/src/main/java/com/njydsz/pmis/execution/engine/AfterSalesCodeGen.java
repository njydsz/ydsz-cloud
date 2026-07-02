package com.njydsz.pmis.execution.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后业务编码生成器
 *
 * <p>格式：{prefix}-yyyyMMdd-XXXX 后 4 位为随机数，避免并发碰撞。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class AfterSalesCodeGen {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private AfterSalesCodeGen() {}

    /**
     * 生成质保单编码
     *
     * @param today 日期；为空时使用当前日期
     * @return 质保单编码（WY-yyyyMMdd-XXXX）
     */
    public static String warrantyCode(LocalDate today) {
        return "WY-" + (today != null ? today : LocalDate.now()).format(DATE)
                + "-" + random4();
    }

    public static String ticketCode(LocalDateTime now) {
        return "TK-" + (now != null ? now : LocalDateTime.now()).format(DATETIME)
                + "-" + random4();
    }

    /**
     * 生成满意度调查编码
     *
     * @param today 日期；为空时使用当前日期
     * @return 调查编码（SV-yyyyMMdd-XXXX）
     */
    public static String surveyCode(LocalDate today) {
        return "SV-" + (today != null ? today : LocalDate.now()).format(DATE)
                + "-" + random4();
    }

    private static String random4() {
        int n = ThreadLocalRandom.current().nextInt(0, 10000);
        return String.format("%04d", n);
    }
}
