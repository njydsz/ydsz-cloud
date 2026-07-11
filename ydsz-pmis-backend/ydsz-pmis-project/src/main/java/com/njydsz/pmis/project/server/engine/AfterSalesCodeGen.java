package com.njydsz.pmis.project.server.engine;

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

    /** 日期格式化器（yyyyMMdd） */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 日期时间格式化器（yyyyMMddHHmmss） */
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 私有构造，工具类不可实例化 */
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

    /**
     * 生成运维工单编码
     *
     * @param now 日期时间；为空时使用当前时间
     * @return 工单编码（TK-yyyyMMddHHmmss-XXXX）
     */
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

    /**
     * 生成 4 位随机数
     *
     * @return 0-9999 的 4 位补零字符串
     */
    private static String random4() {
        int n = ThreadLocalRandom.current().nextInt(0, 10000);
        return String.format("%04d", n);
    }
}
