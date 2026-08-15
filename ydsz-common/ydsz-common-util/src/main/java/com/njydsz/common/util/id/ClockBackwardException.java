package com.njydsz.common.util.id;

/**
 * 时钟回拨异常
 *
 * <p>当系统时钟回拨量超过容忍阈值时抛出此异常。
 * Snowflake 算法依赖单调递增的时间戳，时钟回拨会导致 ID 重复。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ClockBackwardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long backwardMillis;
    private final long lastTimestamp;
    private final long currentTime;

    /**
     * 构造时钟回拨异常
     *
     * @param backwardMillis 回拨毫秒数
     * @param lastTimestamp  上次生成 ID 的时间戳
     * @param currentTime    当前时间戳
     * @return 处理后的结果
     */
    public ClockBackwardException(long backwardMillis, long lastTimestamp, long currentTime) {
        super(String.format("Clock moved backwards by %d ms. Last timestamp: %d, current: %d",
                backwardMillis, lastTimestamp, currentTime));
        this.backwardMillis = backwardMillis;
        this.lastTimestamp = lastTimestamp;
        this.currentTime = currentTime;
    }

    /**
     * 获取回拨毫秒数
     *
     * @return 回拨毫秒数
     */
    public long getBackwardMillis() {
        return backwardMillis;
    }

    /**
     * 获取上次生成 ID 的时间戳
     *
     * @return 上次时间戳
     */
    public long getLastTimestamp() {
        return lastTimestamp;
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳
     */
    public long getCurrentTime() {
        return currentTime;
    }
}

