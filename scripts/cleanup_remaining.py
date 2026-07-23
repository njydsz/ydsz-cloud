#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phase 3: Clean up remaining @Deprecated code and broken references.
- Delete files referencing deleted JobRelationDO
- Fix DagParser/DependencyPatrolScanner
- Fix CacheType enum (restore non-deprecated values)
- Replace CacheType.TTL with CacheType.STRIPED
- Remove remaining @Deprecated annotations
- Fix PageResponse.of/getList references
- Fix Javadoc references to deleted classes
"""

import os
import re
import pathlib

ROOT = pathlib.Path(r"d:/Code/ydsz/ydsz-pmis")

# ============================================================
# 1. Delete files that reference deleted JobRelationDO
# ============================================================
FILES_TO_DELETE = [
    "ydsz-backend/ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobRelationMapper.java",
    "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/DagExecutor.java",
]

# ============================================================
# 2. Fix DagParser.java - remove JobRelationDO references
# ============================================================
DAG_PARSER_PATH = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/DagParser.java"
DAG_PARSER_NEW = '''package com.njydsz.cronjob.server.core.dag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.dag.DagGraph;

/**
 * DAG 解析器（P0-1 架构优化：委托到 common.DagGraph）。
 *
 * <p>纯拓扑算法委托到 {@link DagGraph} 统一实现。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #topologicalSort(Map)}：委托 {@link DagGraph#topologicalSort}</li>
 *   <li>{@link #hasCycle(Map)}：委托 {@link DagGraph#hasCycle}</li>
 *   <li>{@link #getDescendants(String, Map)}：委托 {@link DagGraph#getDescendants}</li>
 *   <li>{@link #getAncestors(String, Map)}：委托 {@link DagGraph#getAncestors}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class DagParser {

    /**
     * 拓扑排序（委托 DagGraph）。
     */
    public List<String> topologicalSort(Map<String, List<String>> adj) {
        return DagGraph.topologicalSort(adj);
    }

    /**
     * 检测环（委托 DagGraph）。
     */
    public boolean hasCycle(Map<String, List<String>> adj) {
        return DagGraph.hasCycle(adj);
    }

    /**
     * 获取所有后代节点（委托 DagGraph）。
     */
    public Set<String> getDescendants(String start, Map<String, List<String>> adj) {
        return DagGraph.getDescendants(start, adj);
    }

    /**
     * 获取所有祖先节点（委托 DagGraph）。
     */
    public Set<String> getAncestors(String target, Map<String, List<String>> adj) {
        return DagGraph.getAncestors(target, adj);
    }
}
'''

# ============================================================
# 3. Fix DependencyPatrolScanner.java - remove JobRelationMapper
# ============================================================
DEPENDENCY_PATROL_PATH = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/DependencyPatrolScanner.java"
DEPENDENCY_PATROL_NEW = '''package com.njydsz.cronjob.server.core.dispatch;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import com.njydsz.cronjob.domain.entity.dag.JobDagDO;
import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagNode;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-10: 依赖巡检与自愈机制。
 *
 * <p>定期扫描 DAG 定义，发现断裂依赖时自动修复：
 * <ul>
 *   <li>DAG 定义引用了已删除/已禁用的任务 → 自动禁用 DAG 并告警</li>
 *   <li>NORMAL 状态的任务引用了不存在的 handler → 自动暂停并告警</li>
 * </ul>
 *
 * <h3>执行策略</h3>
 * <ul>
 *   <li>仅 Leader 节点执行（避免多节点重复扫描）</li>
 *   <li>默认每 10 分钟扫描一次</li>
 *   <li>扫描结果记录到日志，可通过告警系统推送</li>
 * </ul>
 *
 * <p>对标 Airflow 的 DAG 解析校验和 PowerJob 的任务健康检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class DependencyPatrolScanner {

    private final JobMapper jobMapper;
    private final JobDagMapper jobDagMapper;
    private final DagDefinitionCodec dagDefinitionCodec;
    private final LeaderElector leaderElector;

    /** Leader 角色 */
    private String leaderRole = "ydsz-job-scheduler";

    /**
     * 定时巡检依赖完整性。
     *
     * <p>默认每 10 分钟执行一次，仅 Leader 节点运行。
     */
    @Scheduled(fixedDelayString = "${ydsz.cronjob.dependency-patrol.interval-ms:600000}")
    public void patrol() {
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            int dagIssues = patrolDagDependencies();
            int handlerIssues = patrolJobHandlers();
            if (dagIssues + handlerIssues > 0) {
                log.warn("[DependencyPatrol] 巡检完成, 发现问题: dagIssues={} handlerIssues={}",
                        dagIssues, handlerIssues);
            } else {
                log.debug("[DependencyPatrol] 巡检完成, 无异常");
            }
        } catch (Exception e) {
            log.error("[DependencyPatrol] 巡检异常: reason={}", e.getMessage(), e);
        }
    }

    /**
     * 巡检 DAG 定义中的节点引用完整性。
     *
     * <p>检查每个 ENABLED 状态的 DAG 定义中引用的 jobKey 是否仍存在且为 NORMAL 状态。
     * 发现断裂依赖时自动禁用 DAG 并记录告警日志。
     *
     * @return 发现的问题数
     */
    private int patrolDagDependencies() {
        int issues = 0;
        List<JobDagDO> enabledDags = jobDagMapper.selectEnabledDags();
        if (enabledDags == null || enabledDags.isEmpty()) {
            return 0;
        }
        for (JobDagDO dag : enabledDags) {
            try {
                DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
                if (definition == null || definition.nodes() == null) {
                    continue;
                }
                // 收集 DAG 中所有引用的 jobKey
                Set<String> referencedJobKeys = definition.nodes().stream()
                        .map(DagNode::jobKey)
                        .collect(Collectors.toSet());
                // 查询这些 jobKey 对应的任务是否存在且为 NORMAL
                for (String jobKey : referencedJobKeys) {
                    JobDO job = jobMapper.selectByJobKey(jobKey);
                    if (job == null) {
                        log.warn("[DependencyPatrol] DAG 引用的任务不存在, 自动禁用: dagKey={} jobKey={}",
                                dag.getDagKey(), jobKey);
                        disableDag(dag, "引用任务不存在: " + jobKey);
                        issues++;
                        break;  // DAG 已禁用，无需继续检查
                    }
                    if (!"NORMAL".equals(job.getStatus()) && !"AUTO_PAUSED".equals(job.getStatus())) {
                        log.warn("[DependencyPatrol] DAG 引用的任务非 NORMAL 状态, 自动禁用: dagKey={} jobKey={} jobStatus={}",
                                dag.getDagKey(), jobKey, job.getStatus());
                        disableDag(dag, "引用任务状态异常: " + jobKey + " status=" + job.getStatus());
                        issues++;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("[DependencyPatrol] DAG 解析异常, 跳过: dagKey={} reason={}",
                        dag.getDagKey(), e.getMessage());
            }
        }
        return issues;
    }

    /**
     * 巡检 NORMAL 状态任务的 handler 引用完整性。
     *
     * <p>检查 NORMAL 状态任务的 handler 字段是否为空。
     * handler 为空的任务无法执行，应自动暂停。
     *
     * @return 发现的问题数
     */
    private int patrolJobHandlers() {
        int issues = 0;
        try {
            List<JobDO> normalJobs = jobMapper.selectAllNormal();
            if (normalJobs == null || normalJobs.isEmpty()) {
                return 0;
            }
            for (JobDO job : normalJobs) {
                if (!StringUtils.hasText(job.getHandler())) {
                    log.warn("[DependencyPatrol] 任务 handler 为空, 自动暂停: jobKey={} jobId={}",
                            job.getJobKey(), job.getId());
                    jobMapper.markAutoPaused(job.getId());
                    issues++;
                }
            }
        } catch (Exception e) {
            log.warn("[DependencyPatrol] handler 巡检异常: reason={}", e.getMessage());
        }
        return issues;
    }

    /**
     * 禁用 DAG 定义并记录原因。
     *
     * @param dag    DAG 定义
     * @param reason 禁用原因
     */
    private void disableDag(JobDagDO dag, String reason) {
        try {
            dag.setStatus("DISABLED");
            dag.setNextFireTime(null);
            dag.setVersion((dag.getVersion() == null ? 0 : dag.getVersion()) + 1);
            jobDagMapper.updateById(dag);
            log.warn("[DependencyPatrol] DAG 已自动禁用: dagKey={} reason={}", dag.getDagKey(), reason);
        } catch (Exception e) {
            log.error("[DependencyPatrol] DAG 禁用失败: dagKey={} reason={}", dag.getDagKey(), e.getMessage());
        }
    }
}
'''

# ============================================================
# 4. Fix CacheType.java - restore non-deprecated values, remove deprecated
# ============================================================
CACHE_TYPE_PATH = "ydsz-backend/ydsz-common/ydsz-common-cache/src/main/java/com/njydsz/common/cache/builder/CacheType.java"
CACHE_TYPE_NEW = '''package com.njydsz.common.cache.builder;

/**
 * 缓存类型枚举
 *
 * <p>支持的缓存类型：
 *
 * <ul>
 *   <li>LRU：最近最少使用淘汰策略
 *   <li>LFU：最不经常使用淘汰策略
 *   <li>TinyLFU：Window-TinyLFU 算法（参考 Caffeine）
 *   <li>Weighted：基于权重的缓存
 *   <li>Concurrent：并发安全的 ConcurrentHashMap 缓存
 *   <li>Striped：高性能分段锁并发缓存（默认）
 *   <li>EnhancedLoading：增强版自动加载缓存
 * </ul>
 *
 * <p><b>引用缓存</b>（通过 CacheBuilder 的 weakKeys/weakValues/softValues 配置，不再作为独立 CacheType）：
 * <ul>
 *   <li>WeakKey：弱引用键缓存 → 使用 {@code builder.weakKeys()}</li>
 *   <li>WeakValue：弱引用值缓存 → 使用 {@code builder.weakValues()}</li>
 *   <li>SoftValue：软引用值缓存 → 使用 {@code builder.softValues()}</li>
 * </ul>
 *
 * <p><b>TTL 缓存</b>：通过 {@code builder.expireAfterWrite()} 或 {@code builder.expireAfterAccess()} 配置，
 * 不再作为独立 CacheType。
 *
 * @since 1.0.0
 */
public enum CacheType {
  /** LRU 最近最少使用淘汰策略 适用场景：热点数据缓存 */
  LRU,

  /** LFU 最不经常使用淘汰策略 适用场景：访问频率差异大的场景 */
  LFU,

  /** Window-TinyLFU 算法（参考 Caffeine） 适用场景：通用场景，命中率最优（默认） */
  TINYLFU,

  /** 基于权重的缓存 适用场景：内存敏感场景，按对象大小淘汰 */
  WEIGHTED,

  /** 并发安全的 ConcurrentHashMap 缓存 适用场景：中等并发场景 */
  CONCURRENT,

  /** 高性能分段锁并发缓存（默认） 适用场景：高并发场景，性能最优 推荐：高并发场景首选 */
  STRIPED,

  /** 增强版自动加载缓存 适用场景：需要自动加载、自动刷新的场景 推荐：数据库查询缓存首选 */
  ENHANCED_LOADING
}
'''

# ============================================================
# 5. Fix RequestContext.java - remove deprecated runWithContext + restore
# ============================================================
REQUEST_CONTEXT_PATH = "ydsz-backend/ydsz-common/ydsz-common-core/src/main/java/com/njydsz/common/core/context/RequestContext.java"

# ============================================================
# 6. Fix ArrayUtils.java - remove deprecated toArray
# ============================================================
ARRAY_UTILS_PATH = "ydsz-backend/ydsz-common/ydsz-common-util/src/main/java/com/njydsz/common/util/array/ArrayUtils.java"

# ============================================================
# 7. Fix AuditProperties.java - remove deprecated duplicate methods
# ============================================================
AUDIT_PROPS_PATH = "ydsz-backend/ydsz-common/ydsz-common-audit/src/main/java/com/njydsz/common/audit/config/AuditProperties.java"


def read_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()


def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)


def delete_file(path):
    if os.path.exists(path):
        os.remove(path)
        print(f"  DELETED: {path}")
    else:
        print(f"  SKIP (not found): {path}")


def main():
    print("=" * 60)
    print("Phase 3: Remaining @Deprecated cleanup")
    print("=" * 60)

    # --- 1. Delete files ---
    print("\n[1] Deleting files referencing deleted JobRelationDO...")
    for f in FILES_TO_DELETE:
        delete_file(str(ROOT / f))

    # --- 2. Rewrite DagParser.java ---
    print("\n[2] Rewriting DagParser.java...")
    write_file(str(ROOT / DAG_PARSER_PATH), DAG_PARSER_NEW)
    print(f"  REWRITTEN: {DAG_PARSER_PATH}")

    # --- 3. Rewrite DependencyPatrolScanner.java ---
    print("\n[3] Rewriting DependencyPatrolScanner.java...")
    write_file(str(ROOT / DEPENDENCY_PATROL_PATH), DEPENDENCY_PATROL_NEW)
    print(f"  REWRITTEN: {DEPENDENCY_PATROL_PATH}")

    # --- 4. Rewrite CacheType.java ---
    print("\n[4] Rewriting CacheType.java...")
    write_file(str(ROOT / CACHE_TYPE_PATH), CACHE_TYPE_NEW)
    print(f"  REWRITTEN: {CACHE_TYPE_PATH}")

    # --- 5. Replace CacheType.TTL with CacheType.STRIPED ---
    print("\n[5] Replacing CacheType.TTL with CacheType.STRIPED...")
    ttl_count = 0
    for root, dirs, files in os.walk(str(ROOT)):
        # Skip .git, target, node_modules
        dirs[:] = [d for d in dirs if d not in ('.git', 'target', 'node_modules')]
        for fname in files:
            if not fname.endswith('.java'):
                continue
            fpath = os.path.join(root, fname)
            content = read_file(fpath)
            if 'CacheType.TTL' in content:
                new_content = content.replace('CacheType.TTL', 'CacheType.STRIPED')
                write_file(fpath, new_content)
                ttl_count += 1
                print(f"  FIXED: {os.path.relpath(fpath, str(ROOT))}")
    print(f"  Total: {ttl_count} files")

    # --- 6. Fix RequestContext.java - remove deprecated runWithContext + restore ---
    print("\n[6] Fixing RequestContext.java...")
    rc_path = str(ROOT / REQUEST_CONTEXT_PATH)
    content = read_file(rc_path)
    # Remove the deprecated runWithContext method and restore method
    # The deprecated block starts with the Javadoc for runWithContext and ends before snapshot()
    old_block = '''    }    /**
     * 在指定上下文中执行 Runnable，执行完毕后自动清除上下文
     *
     * <p>用于异步场景：先在父线程通过 {@link #capture()} 捕获上下文，
     * 再在子线程中调用此方法恢复上下文执行逻辑。</p>
     *
     * @param context  通过 {@link #capture()} 捕获的上下文
     * @param runnable 要执行的逻辑
     * @deprecated 使用 TransmittableThreadLocal + TtlExecutors 自动传播替代
     */
    @Deprecated
    public static void runWithContext(Map<String, Object> context, Runnable runnable) {
        try {
            restore(context);
            runnable.run();
        } finally {
            clear();
        }
    }
    /**
     * 获取当前上下文快照
     *
     * @return 上下文 Map 的副本
     */
    public static Map<String, Object> snapshot() {
        return new HashMap<>(CONTEXT_HOLDER.get());
    }

    /**
     * 恢复上下文到当前线程
     *
     * <p>先清除当前线程已有的上下文，再将指定的上下文快照恢复到当前线程。
     * 用于异步场景中子线程恢复父线程捕获的上下文。</p>
     *
     * @param context 通过 {@link #capture()} 捕获的上下文快照
     */
    private static void restore(Map<String, Object> context) {
        CONTEXT_HOLDER.remove();
        if (context != null && !context.isEmpty()) {
            CONTEXT_HOLDER.set(new HashMap<>(context));
        }
    }

    /**
     * 创建一个上下文清理守卫，用于 try-with-resources 模式'''

    new_block = '''    }

    /**
     * 获取当前上下文快照
     *
     * @return 上下文 Map 的副本
     */
    public static Map<String, Object> snapshot() {
        return new HashMap<>(CONTEXT_HOLDER.get());
    }

    /**
     * 创建一个上下文清理守卫，用于 try-with-resources 模式'''

    if old_block in content:
        content = content.replace(old_block, new_block)
        write_file(rc_path, content)
        print(f"  FIXED: {REQUEST_CONTEXT_PATH}")
    else:
        print(f"  WARNING: Could not find deprecated block in RequestContext.java")

    # --- 7. Fix ArrayUtils.java - remove deprecated toArray ---
    print("\n[7] Fixing ArrayUtils.java...")
    au_path = str(ROOT / ARRAY_UTILS_PATH)
    content = read_file(au_path)
    old_toarray = '''    /**
     * 将 Collection 转换为数组
     *
     * @deprecated 请使用 {@link com.njydsz.common.util.collection.CollectionUtils#listToArray(Collection, Class)} 替代，
     *             CollectionUtils 更专注于集合操作且类型安全签名更明确
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public static <T> T[] toArray(Collection<T> collection, Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        if (collection == null || collection.isEmpty()) {
            return newArray(clazz, 0);
        }
        return collection.toArray(newArray(clazz, collection.size()));
    }

    '''
    if old_toarray in content:
        content = content.replace(old_toarray, '')
        write_file(au_path, content)
        print(f"  FIXED: {ARRAY_UTILS_PATH}")
    else:
        print(f"  WARNING: Could not find deprecated toArray in ArrayUtils.java")

    # --- 8. Fix AuditProperties.java - remove deprecated duplicate methods ---
    print("\n[8] Fixing AuditProperties.java...")
    ap_path = str(ROOT / AUDIT_PROPS_PATH)
    content = read_file(ap_path)
    old_deprecated = '''    /**
     * @deprecated 使用 {@link #getExecutorQueueCapacity()} 替代
     */
    @Deprecated
    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    /**
     * @deprecated 使用 {@link #setExecutorQueueCapacity(int)} 替代
     */
    @Deprecated
    public void setExecutorQueueCapacity(int queueCapacity) {
        this.executorQueueCapacity = queueCapacity;
    }

    '''
    if old_deprecated in content:
        content = content.replace(old_deprecated, '')
        write_file(ap_path, content)
        print(f"  FIXED: {AUDIT_PROPS_PATH}")
    else:
        print(f"  WARNING: Could not find deprecated methods in AuditProperties.java")

    # --- 9. Fix PageResponse.of(List,...) and getList() in test files ---
    print("\n[9] Fixing PageResponse.of/getList references...")

    # Fix FlowMonitorController.java
    flow_monitor = "ydsz-backend/ydsz-workflow/ydsz-workflow-web/src/main/java/com/njydsz/workflow/web/controller/FlowMonitorController.java"
    fpath = str(ROOT / flow_monitor)
    content = read_file(fpath)
    content = re.sub(
        r'PageResponse\.of\((page),\s*(total),\s*(pageNum),\s*(pageSize)\)',
        r'PageResponse.success(\2, \3, \4, \1)',
        content
    )
    write_file(fpath, content)
    print(f"  FIXED: {flow_monitor}")

    # Fix FlowCountersignHistoryController.java
    flow_counter = "ydsz-backend/ydsz-workflow/ydsz-workflow-web/src/main/java/com/njydsz/workflow/web/controller/FlowCountersignHistoryController.java"
    fpath = str(ROOT / flow_counter)
    content = read_file(fpath)
    content = re.sub(
        r'PageResponse\.of\((pageData),\s*(total),\s*(pageNo),\s*(pageSize)\)',
        r'PageResponse.success(\2, \3, \4, \1)',
        content
    )
    write_file(fpath, content)
    print(f"  FIXED: {flow_counter}")

    # Fix BaseResponseTest.java - replace PageResponse.of(List, ...) with success(...)
    test_path = "ydsz-backend/ydsz-common/ydsz-common-core/src/test/java/com/njydsz/common/core/response/BaseResponseTest.java"
    fpath = str(ROOT / test_path)
    content = read_file(fpath)
    # Replace the test that uses PageResponse.of(List.of(...), ...) and getList()
    old_test = '''        @Test
        @DisplayName("getList 从 data 中提取列表（向后兼容模式：T 为元素类型）")
        void getList() {
            PageResponse<String> resp = PageResponse.of(List.of("a", "b"), 10L, 1L, 10L);
            assertThat(resp.getList()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("getList data 非 List 时返回空列表")
        void getList_notList() {
            PageResponse<String> resp = PageResponse.success("not a list");
            assertThat(resp.getList()).isEmpty();
        }'''
    new_test = '''        @Test
        @DisplayName("success 构建分页响应并获取数据")
        void success_withData() {
            PageResponse<List<String>> resp = PageResponse.success(10L, 1L, 10L, List.of("a", "b"));
            assertThat(resp.getData()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("data 为 null 时返回 null")
        void data_null() {
            PageResponse<List<String>> resp = PageResponse.success(10L, 1L, 10L, null);
            assertThat(resp.getData()).isNull();
        }'''
    if old_test in content:
        content = content.replace(old_test, new_test)
        write_file(fpath, content)
        print(f"  FIXED: {test_path}")
    else:
        print(f"  WARNING: Could not find test block in BaseResponseTest.java")

    # Fix RequestContextTest.java - remove tests for deprecated methods
    rc_test = "ydsz-backend/ydsz-common/ydsz-common-core/src/test/java/com/njydsz/common/core/context/RequestContextTest.java"
    fpath = str(ROOT / rc_test)
    content = read_file(fpath)
    # Remove the entire AsyncPropagation test class
    old_async = '''    @Nested
    @DisplayName("异步上下文传播（已废弃 API 的向后兼容测试）")
    class AsyncPropagation {

        @Test
        @DisplayName("capture + wrapCallable 传播上下文到子线程")
        void captureAndWrapCallable() throws Exception {
            RequestContext.setUserId("user-001");
            RequestContext.setTenantId("tenant-001");

            Map<String, Object> captured = RequestContext.snapshot();
            RequestContext.clear();

            java.util.concurrent.Callable<String> task = RequestContext.wrapCallable(
                    () -> RequestContext.getUserId() + "|" + RequestContext.getTenantId(),
                    captured);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    return "error";
                }
            });

            assertThat(future.get()).isEqualTo("user-001|tenant-001");
        }

        @Test
        @DisplayName("runWithContext 在指定上下文中执行后清理")
        void runWithContext() {
            RequestContext.setUserId("user-001");
            Map<String, Object> captured = RequestContext.snapshot();
            RequestContext.clear();

            String result = RequestContext.runWithContext(captured, () -> RequestContext.getUserId());
            assertThat(result).isEqualTo("user-001");
            assertThat(RequestContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("snapshot 返回当前上下文副本")
        void snapshot() {
            RequestContext.setUserId("user-001");
            Map<String, Object> snap = RequestContext.snapshot();
            assertThat(snap).containsEntry("userId", "user-001");
            // snapshot 不影响原始上下文
            assertThat(RequestContext.getUserId()).isEqualTo("user-001");
        }
    }'''
    new_async = '''    @Nested
    @DisplayName("上下文快照")
    class Snapshot {

        @Test
        @DisplayName("snapshot 返回当前上下文副本")
        void snapshot() {
            RequestContext.setUserId("user-001");
            Map<String, Object> snap = RequestContext.snapshot();
            assertThat(snap).containsEntry("userId", "user-001");
            // snapshot 不影响原始上下文
            assertThat(RequestContext.getUserId()).isEqualTo("user-001");
        }
    }'''
    if old_async in content:
        content = content.replace(old_async, new_async)
        # Remove unused import CompletableFuture
        content = content.replace('import java.util.concurrent.CompletableFuture;\n', '')
        write_file(fpath, content)
        print(f"  FIXED: {rc_test}")
    else:
        print(f"  WARNING: Could not find AsyncPropagation in RequestContextTest.java")

    # --- 10. Fix Javadoc references to deleted classes ---
    print("\n[10] Fixing Javadoc references to deleted classes...")

    javadoc_fixes = {
        'TraceIdUtil': 'TracerUtils',
        'CryptoSignUtil': 'DigestUtils',
        'PermissionContextHolder': 'AuthContext',
    }

    # Specific file-level fixes
    specific_fixes = []

    # DingTalkSignatureUtil.java - CryptoSignUtil reference
    ding_sig = "ydsz-backend/ydsz-workflow/ydsz-workflow-server/src/main/java/com/njydsz/workflow/server/thirdparty/DingTalkSignatureUtil.java"
    fpath = str(ROOT / ding_sig)
    content = read_file(fpath)
    content = content.replace(
        '{@link CryptoSignUtil}',
        'DigestUtils'
    )
    write_file(fpath, content)
    specific_fixes.append(ding_sig)

    # DingTalkChannel.java - CryptoSignUtil reference
    ding_ch = "ydsz-backend/ydsz-message/ydsz-message-server/src/main/java/com/njydsz/message/server/channel/DingTalkChannel.java"
    fpath = str(ROOT / ding_ch)
    content = read_file(fpath)
    content = content.replace('CryptoSignUtil', 'DigestUtils')
    write_file(fpath, content)
    specific_fixes.append(ding_ch)

    # AuthContext.java - PermissionContextHolder reference
    auth_ctx = "ydsz-backend/ydsz-common/ydsz-common-auth/src/main/java/com/njydsz/common/auth/context/AuthContext.java"
    fpath = str(ROOT / auth_ctx)
    content = read_file(fpath)
    content = content.replace(
        'PermissionContextHolder/ColumnPermissionContext',
        'ColumnPermissionContext'
    )
    write_file(fpath, content)
    specific_fixes.append(auth_ctx)

    # MessageTraceContext.java - TraceIdUtil references
    msg_tc = "ydsz-backend/ydsz-message/ydsz-message-server/src/main/java/com/njydsz/message/server/tracing/MessageTraceContext.java"
    fpath = str(ROOT / msg_tc)
    content = read_file(fpath)
    content = content.replace('{@link TraceIdUtil#getOrCreate()}', '{@link TracerUtils#getOrCreate()}')
    content = content.replace('{@link TraceIdUtil#get()}', '{@link TracerUtils#get()}')
    write_file(fpath, content)
    specific_fixes.append(msg_tc)

    # TraceIdGenerator.java - TraceIdUtil reference
    trace_gen = "ydsz-backend/ydsz-common/ydsz-common-core/src/main/java/com/njydsz/common/core/trace/TraceIdGenerator.java"
    fpath = str(ROOT / trace_gen)
    content = read_file(fpath)
    content = content.replace('TraceFilter、TraceIdUtil', 'TraceFilter、TracerUtils')
    write_file(fpath, content)
    specific_fixes.append(trace_gen)

    # JobScanner.java - TraceIdUtil reference
    job_scanner = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/JobScanner.java"
    fpath = str(ROOT / job_scanner)
    content = read_file(fpath)
    content = content.replace('{@link TraceIdUtil#getOrCreate()}', '{@link TracerUtils#getOrCreate()}')
    write_file(fpath, content)
    specific_fixes.append(job_scanner)

    # TraceIntegrationHelper.java - TraceIdUtil reference
    trace_helper = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/TraceIntegrationHelper.java"
    fpath = str(ROOT / trace_helper)
    content = read_file(fpath)
    content = content.replace('MDC + TraceIdUtil', 'MDC + TracerUtils')
    write_file(fpath, content)
    specific_fixes.append(trace_helper)

    # IpWhitelistFilter.java - TraceIdUtil reference
    ip_filter = "ydsz-backend/ydsz-gateway/src/main/java/com/njydsz/gateway/filter/IpWhitelistFilter.java"
    fpath = str(ROOT / ip_filter)
    content = read_file(fpath)
    content = content.replace('TraceIdUtil', 'TracerUtils')
    write_file(fpath, content)
    specific_fixes.append(ip_filter)

    # DagEdge.java - FailStrategy Javadoc reference
    dag_edge = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/DagEdge.java"
    fpath = str(ROOT / dag_edge)
    content = read_file(fpath)
    content = content.replace('{@link FailStrategy}', '{@link DagFailureStrategy}')
    write_file(fpath, content)
    specific_fixes.append(dag_edge)

    # TaskCompletedEvent.java - DagExecutor reference
    task_event = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/TaskCompletedEvent.java"
    fpath = str(ROOT / task_event)
    content = read_file(fpath)
    content = content.replace(
        '各监听器（{@code DagExecutor}、{@code DagInstanceExecutor}、',
        '各监听器（{@code DagInstanceExecutor}、'
    )
    write_file(fpath, content)
    specific_fixes.append(task_event)

    # DefaultTaskDispatcher.java - DagExecutor comment
    default_disp = "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/DefaultTaskDispatcher.java"
    fpath = str(ROOT / default_disp)
    content = read_file(fpath)
    content = content.replace(
        '触发后继依赖任务（DagExecutor 异步监听）',
        '触发后继依赖任务（DagInstanceExecutor 异步监听）'
    )
    write_file(fpath, content)
    specific_fixes.append(default_disp)

    # DagExecutor.java Javadoc in DagExecutor.java has FailStrategy reference - but we deleted that file

    for f in specific_fixes:
        print(f"  FIXED: {f}")

    # --- 11. Fix YdszCache.java Javadoc that mentions removed enum values ---
    print("\n[11] Fixing YdszCache.java Javadoc...")
    ydsz_cache = "ydsz-backend/ydsz-common/ydsz-common-cache/src/main/java/com/njydsz/common/cache/YdszCache.java"
    fpath = str(ROOT / ydsz_cache)
    content = read_file(fpath)
    content = content.replace(
        'TINYLFU、LRU、LFU、TTL、WEIGHTED、WEAK_KEY、WEAK_VALUE、SOFT_VALUE、CONCURRENT、STRIPED、ENHANCED_LOADING',
        'TINYLFU、LRU、LFU、WEIGHTED、CONCURRENT、STRIPED、ENHANCED_LOADING'
    )
    write_file(fpath, content)
    print(f"  FIXED: {ydsz_cache}")

    # --- 12. Fix CacheBuilderTest.java - CacheType.TTL already replaced ---
    # (Already handled by step 5)

    print("\n" + "=" * 60)
    print("Phase 3 cleanup complete!")
    print("=" * 60)


if __name__ == '__main__':
    main()
