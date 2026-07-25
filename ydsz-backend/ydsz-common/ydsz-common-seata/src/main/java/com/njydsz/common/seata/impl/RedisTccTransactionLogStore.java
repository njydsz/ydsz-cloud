package com.njydsz.common.seata.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.njydsz.common.seata.api.TccBranchStatus;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/**
 * 基于 Redis 的 TCC 事务日志存储
 *
 * <p>使用 Redis Hash 存储事务日志，适用于生产环境的分布式部署。
 * 相比 {@link InMemoryTccTransactionLogStore}，本实现支持：
 * <ul>
 *   <li><b>跨服务共享</b>：TCC 协调器可在多个服务实例间共享事务状态，
 *       任一实例均可执行 Confirm/Cancel 恢复</li>
 *   <li><b>持久化</b>：服务重启后事务状态不丢失，支持启动时恢复未完成事务</li>
 *   <li><b>自动过期</b>：终态事务日志在 TTL 后自动清理，避免无限累积</li>
 *   <li><b>SCAN 遍历</b>：使用 {@code SCAN} 命令遍历 key，避免 {@code KEYS} 阻塞 Redis</li>
 * </ul>
 *
 * <p><b>存储结构</b>：
 * <pre>
 *   Key:   {keyPrefix}:{xid}:{branchId}    (Redis Hash)
 *   Field: xid / branchId / transactionName / status / contextSnapshot /
 *          tryStartedAt / tryCompletedAt / finishedAt / retryCount / lastError
 *   TTL:   retentionHours (默认 24 小时)
 * </pre>
 *
 * <p><b>线程安全</b>：{@link RedisTemplate} 自身线程安全，本实现无额外共享状态。
 *
 * <p><b>注册方式</b>：通过 {@link com.njydsz.common.seata.config.SeataAutoConfiguration}
 * 在 {@code RedisTemplate} 可用时自动注册，可通过
 * {@code ydsz.seata.tcc-log-store=redis} 显式启用，{@code =memory} 回退到内存版。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisTccTransactionLogStore implements TccTransactionLogStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTccTransactionLogStore.class);

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String FIELD_XID = "xid";
    private static final String FIELD_BRANCH_ID = "branchId";
    private static final String FIELD_TX_NAME = "transactionName";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CTX_SNAPSHOT = "contextSnapshot";
    private static final String FIELD_TRY_STARTED_AT = "tryStartedAt";
    private static final String FIELD_TRY_COMPLETED_AT = "tryCompletedAt";
    private static final String FIELD_FINISHED_AT = "finishedAt";
    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final String FIELD_LAST_ERROR = "lastError";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String