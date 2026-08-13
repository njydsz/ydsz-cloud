package com.njydsz.common.util.id;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于本地文件持久化的 WorkerId 分配器——开发环境兜底策略。
 *
 * <p>在 {@code ~/.{appName}/workerId} 文件中保存已分配的 workerId，
 * 进程重启后返回相同的 workerId，保证开发环境 ID 幂等。
 *
 * <p>文件不存在时随机生成一个 0-1023 范围内的 workerId 并持久化。
 *
 * <p><b>仅限开发环境：</b>多机部署时各节点文件独立，无法保证全局唯一。
 * 生产环境应使用 {@link PodOrdinalWorkerIdAllocator} 或中心化注册。
 *
 * @author ydsz-team
 * @since 3.0.0
 * @deprecated 自 3.1.0 起标记废弃（forRemoval=true）。开发环境推荐通过配置
 *             {@code ydsz.util.snowflake.worker-id} 显式指定 WorkerId，无需文件持久化。
 *             生产环境推荐使用 K8s Pod 序号（{@link PodOrdinalWorkerIdAllocator}）或 IP 哈希
 *             （{@link IpHashWorkerIdAllocator}）。
 */
public final class FilePersistedWorkerIdAllocator implements WorkerIdAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(FilePersistedWorkerIdAllocator.class);

    private static final int MAX_WORKER_ID = 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String appName;

    public FilePersistedWorkerIdAllocator() {
        this("ydsz-app");
    }

    public FilePersistedWorkerIdAllocator(String appName) {
        this.appName = appName;
    }

    @Override
    public int allocate(String nodeId) {
        Path workerIdFile = getWorkerIdFile();

        // 已存在则直接读取
        if (Files.exists(workerIdFile)) {
            try {
                String content = Files.readString(workerIdFile).trim();
                int workerId = Integer.parseInt(content);
                if (workerId >= 0 && workerId < MAX_WORKER_ID) {
                    LOG.info("WorkerId={} loaded from file: {}", workerId, workerIdFile);
                    return workerId;
                }
                LOG.warn("Invalid workerId in file (out of range), regenerating: {}", workerId);
            } catch (NumberFormatException | IOException e) {
                LOG.warn("Failed to read workerId file, regenerating: {}", e.getMessage());
            }
        }

        // 随机生成并持久化
        int workerId = RANDOM.nextInt(MAX_WORKER_ID);
        try {
            Files.createDirectories(workerIdFile.getParent());
            Files.writeString(workerIdFile, String.valueOf(workerId));
            LOG.info("WorkerId={} generated and persisted to: {}", workerId, workerIdFile);
        } catch (IOException e) {
            LOG.warn("Failed to persist workerId to file: {}", e.getMessage());
        }

        return workerId;
    }

    @Override
    public String name() {
        return "FilePersisted";
    }

    private Path getWorkerIdFile() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "." + appName, "workerId");
    }
}
