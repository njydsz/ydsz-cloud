package com.njydsz.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 K8s StatefulSet Pod 序号的 WorkerId 分配器。
 *
 * <p>依赖环境变量 HOSTNAME 符合 StatefulSet 命名模式：{@code <statefulset-name>-<ordinal>}。
 * 如 HOSTNAME={@code order-service-0} → workerId=0。
 *
 * <p>自动感知 Pod 重启后序号不变（StatefulSet 保证），保证 workerId 幂等。
 *
 * <p><b>自动检测：</b>仅在 HOSTNAME 匹配模式时启用，否则抛出 {@link NotApplicableException}
 * 让位给下个策略。
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class PodOrdinalWorkerIdAllocator implements WorkerIdAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(PodOrdinalWorkerIdAllocator.class);

    private static final Pattern STATEFULSET_PATTERN = Pattern.compile("^(.+)-(\\d+)$");
    private static final int MAX_WORKER_ID = 1024;

    @Override
    public int allocate(String nodeId) {
        String hostname = resolveHostname(nodeId);
        Matcher matcher = STATEFULSET_PATTERN.matcher(hostname);
        if (!matcher.matches()) {
            throw new NotApplicableException(
                    "Hostname does not match StatefulSet pattern: '" + hostname + "'");
        }

        int ordinal = Integer.parseInt(matcher.group(2));
        if (ordinal >= MAX_WORKER_ID) {
            throw new WorkerIdExhaustedException(
                    "Pod ordinal " + ordinal + " exceeds max workerId " + (MAX_WORKER_ID - 1));
        }

        LOG.info("WorkerId={} allocated by PodOrdinal (hostname={})", ordinal, hostname);
        return ordinal;
    }

    @Override
    public String name() {
        return "PodOrdinal";
    }

    /**
     * 解析当前节点 hostname，依次尝试：HOSTNAME 环境变量 → POD_NAME 环境变量 → InetAddress。
      * @param nodeId nodeId
      * @return 处理后的结果
     */
    private String resolveHostname(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            return nodeId;
        }
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        hostname = System.getenv("POD_NAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new WorkerIdExhaustedException("Cannot resolve hostname", e);
        }
    }
}


















