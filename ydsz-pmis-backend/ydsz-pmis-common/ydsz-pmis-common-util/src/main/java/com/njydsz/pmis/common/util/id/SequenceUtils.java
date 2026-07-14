package com.njydsz.pmis.common.util.id;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.pmis.common.util.ip.MacAddressUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 高性能业务流水号生成工具类
 * <p>
 * 参考支付宝、微信支付等流水号生成规范实现。
 * 支持生成可读性强、业务友好的流水号。
 * </p>
 * <p>
 * 特性：
 * 1. 支持自定义节点号（MAC 地址映射）
 * 2. 支持多种格式（日期 + 节点 + 序列号）
 * 3. 支持高并发场景
 * 4. 支持解析流水号信息
 * </p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Slf4j
public final class SequenceUtils {

    private SequenceUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }
    private static final String DEFAULT_NODE = "01";
    private static final String FALLBACK_NODE = "--";
    private static final int MAX_SEQ = 9999;
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final AtomicLong lastTimestamp = new AtomicLong(0);
    private static final AtomicInteger sequence = new AtomicInteger(0);
    private static volatile String nodeNumber = FALLBACK_NODE;

    /**
     * 生成下一个业务流水号 (格式：yyyyMMdd + node + HHmmss + 4 位自增序列)
     *
     * @return 业务流水号
     */
    public static String next() {
        String currentTime = ensureTimestampUpdated();
        int seq = nextSequence();

        return new StringBuilder(20)
                .append(currentTime, 0, 8)
                .append(nodeNumber)
                .append(currentTime, 8, 14)
                .append(String.format("%04d", seq))
                .toString();
    }

    /**
     * 初始化节点号 (支持 MAC 地址映射)
     */
    public static synchronized String initNodeNumber(String nodeConfig) {
        if (FALLBACK_NODE.equals(nodeNumber)) {
            if (nodeConfig != null && !nodeConfig.isEmpty()) {
                if (nodeConfig.contains(",")) {
                    String mac = MacAddressUtils.getAllHostMacAddress();
                    for (String entry : nodeConfig.split(",")) {
                        String[] parts = entry.split("=");
                        if (parts.length == 2 && mac.contains(parts[0])) {
                            nodeNumber = parts[1];
                            break;
                        }
                    }
                    if (FALLBACK_NODE.equals(nodeNumber)) {
                        nodeNumber = DEFAULT_NODE;
                    }
                } else {
                    nodeNumber = nodeConfig;
                }

                if (nodeNumber.length() != 2) {
                    throw new IllegalArgumentException("Node number must be 2 characters");
                }
                log.info("SequenceUtils -> Current Node: {}", nodeNumber);
            } else {
                nodeNumber = DEFAULT_NODE;
            }
        }
        return nodeNumber;
    }

    /**
     * 获取当前节点号
     *
     * @return 节点号
     */
    public static String getNodeNumber() {
        return nodeNumber;
    }

    /**
     * 设置节点号（用于动态切换）
     *
     * @param node 节点号
     */
    public static synchronized void setNodeNumber(String node) {
        if (node != null && node.length() == 2) {
            nodeNumber = node;
            log.info("SequenceUtils -> Node changed to: {}", node);
        }
    }

    /**
     * 获取当前序列号
     *
     * @return 序列号
     */
    public static int getCurrentSequence() {
        return sequence.get();
    }

    /**
     * 重置序列号（用于特殊场景）
     */
    public static synchronized void resetSequence() {
        sequence.set(0);
        lastTimestamp.set(0);
    }

    private static int nextSequence() {
        int current;
        int next;
        do {
            current = sequence.get();
            next = (current >= MAX_SEQ) ? 1 : current + 1;
        } while (!sequence.compareAndSet(current, next));
        return next;
    }

    /**
     * 解析流水号中的日期
     *
     * @param sequenceNo 流水号
     * @return 日期字符串（yyyyMMdd）
     */
    public static String parseDate(String sequenceNo) {
        if (sequenceNo == null || sequenceNo.length() < 8) {
            return "";
        }
        return sequenceNo.substring(0, 8);
    }

    /**
     * 解析流水号中的节点号
     *
     * @param sequenceNo 流水号
     * @return 节点号
     */
    public static String parseNode(String sequenceNo) {
        if (sequenceNo == null || sequenceNo.length() < 10) {
            return "";
        }
        return sequenceNo.substring(8, 10);
    }

    /**
     * 解析流水号中的时间
     *
     * @param sequenceNo 流水号
     * @return 时间字符串（HHmmss）
     */
    public static String parseTime(String sequenceNo) {
        if (sequenceNo == null || sequenceNo.length() < 16) {
            return "";
        }
        return sequenceNo.substring(10, 16);
    }

    /**
     * 解析流水号中的序列号
     *
     * @param sequenceNo 流水号
     * @return 序列号
     */
    public static int parseSequence(String sequenceNo) {
        if (sequenceNo == null || sequenceNo.length() < 20) {
            return 0;
        }
        try {
            return Integer.parseInt(sequenceNo.substring(16, 20));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String ensureTimestampUpdated() {
        String current = LocalDateTime.now().format(DT_FORMATTER);
        long currentLong = Long.parseLong(current);
        long last = lastTimestamp.get();
        if (currentLong != last) {
            if (lastTimestamp.compareAndSet(last, currentLong)) {
                sequence.set(0);
            }
        }
        return current;
    }
}
