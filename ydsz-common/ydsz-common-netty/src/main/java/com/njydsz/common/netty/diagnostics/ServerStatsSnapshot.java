package com.njydsz.common.netty.diagnostics;

import lombok.Builder;
import lombok.Data;

/**
 * Server 运行时统计快照。
 *
 * <p>用于管理端点/监控暴露，包含连接数、流量、Pipeline 等运行时信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class ServerStatsSnapshot {

    /** Server 名称（类名） */
    private String serverName;

    /** 监听端口 */
    private int port;

    /** 当前活跃连接数 */
    private int activeConnections;

    /** 累计连接总数 */
    private long totalConnections;

    /** 累计接收字节数 */
    private long totalBytesRead;

    /** 累计发送字节数 */
    private long totalBytesWritten;

    /** 累计重连次数 */
    private long reconnectAttempts;

    /** 累计重连成功次数 */
    private long reconnectSuccesses;

    /** SSL 是否启用 */
    private boolean sslEnabled;

    /** 流量整形是否启用 */
    private boolean trafficShapingEnabled;

    /** 传输类型（Epoll/KQueue/NIO） */
    private String transportType;

    /** Pipeline Handler 名称列表 */
    private java.util.List<String> pipelineHandlers;
}
