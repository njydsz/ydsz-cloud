package com.njydsz.common.util.id;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机数/随机字符串工具类。
 *
 * <p>封装 {@link ThreadLocalRandom} 提供常用的随机操作，统一项目的随机数生成入口。
 * 相比于直接使用 ThreadLocalRandom，本类提供更清晰的 API 和边界处理。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
public final class RandomUtils {

    private RandomUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 [0, bound) 范围内的随机 int。
     *
     * @param bound 上界（不含），> 0
     * @return 随机整数
     */
    public static int randomInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /**
     * 生成 [origin, bound) 范围内的随机 int。
     *
     * @param origin 下界（含）
     * @param bound  上界（不含），> origin
     * @return 随机整数
     */
    public static int randomInt(int origin, int bound) {
        return ThreadLocalRandom.current().nextInt(origin, bound);
    }

    /**
     * 生成随机 long。
     *
     * @return 随机长整数
     */
    public static long randomLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    /**
     * 生成 [origin, bound) 范围内的随机 long。
     *
     * @param origin 下界（含）
     * @param bound  上界（不含）
     * @return 随机长整数
     */
    public static long randomLong(long origin, long bound) {
        return ThreadLocalRandom.current().nextLong(origin, bound);
    }

    /**
     * 生成 [0.0, 1.0) 范围内的随机 double。
     *
     * @return 随机浮点数
     */
    public static double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /**
     * 以给定概率返回 true。
     *
     * @param probability 概率 [0.0, 1.0]
     * @return 按概率随机返回 true
     */
    public static boolean randomBoolean(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }
}










