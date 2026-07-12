package com.njydsz.pmis.message.server.service.impl.core;

import com.njydsz.pmis.message.server.service.core.MsgLogArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息发送日志归档服务实现。
 *
 * <p>调度策略：每月 1 号 02:30 执行一次（避开 0 点业务高峰）。
 * <ol>
 *   <li>归档 90 天前所在月份的分区（DETACH + RENAME）</li>
 *   <li>为下下个月预创建分区（保证至少有一个未来分区可用）</li>
 * </ol>
 *
 * <p>幂等性：
 * <ul>
 *   <li>DETACH 使用 CONCURRENTLY（PG 14+），不阻塞读写</li>
 *   <li>若分区不存在则跳过，记 WARN 日志</li>
 *   <li>CREATE TABLE IF NOT EXISTS 保证幂等</li>
 *   <li>RENAME 失败时（目标表已存在）记 ERROR 但不中断后续流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "pmis.message", name = "archive-enabled", havingValue = "true", matchIfMissing = true)
public class MsgLogArchiveServiceImpl implements MsgLogArchiveService {

    /** 父表名 */
    private static final String PARENT_TABLE = "pmis_msg_log";
    /** 分区名前缀 */
    private static final String PARTITION_PREFIX = "pmis_msg_log_y";
    /** 归档表名前缀 */
    private static final String ARCHIVE_PREFIX = "pmis_msg_log_archive_";

    /** JDBC 模板（分区归档 DDL 执行） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 每月 1 号 02:30 归档 90 天前分区 + 预创建下个月分区。
     *
     * <p>cron: 秒 分 时 日 月 周。{@code 0 30 2 1 * *} = 每月 1 号 02:30:00。
     */
    @Scheduled(cron = "${pmis.message.archive-cron:0 30 2 1 * *}")
    public void scheduledArchive() {
        LocalDate today = LocalDate.now();
        log.info("[MsgLogArchive] 月度归档任务触发: {}", today);
        try {
            List<String> archived = archive(today.getYear(), today.getMonthValue());
            log.info("[MsgLogArchive] 归档完成: {}", archived);
        } catch (Exception e) {
            log.error("[MsgLogArchive] 归档失败: {}", e.getMessage(), e);
        }
        // 为下下个月预创建分区（确保始终有至少 1 个未来分区）
        try {
            YearMonth nextNext = YearMonth.now().plusMonths(2);
            String created = ensurePartition(nextNext.getYear(), nextNext.getMonthValue());
            if (created != null) {
                log.info("[MsgLogArchive] 预创建分区: {}", created);
            }
        } catch (Exception e) {
            log.error("[MsgLogArchive] 预创建分区失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> archive(int year, int month) {
        // 90 天前 ≈ 当前月份 - 3
        YearMonth target = YearMonth.of(year, month).minusMonths(3);
        List<String> archived = new ArrayList<>();

        // 从 90 天前向前回溯，归档所有早于该月份的分区（应对历史漏归档场景）
        // 上限回溯 24 个月，避免一次性 DETACH 太多
        for (int i = 0; i <= 24; i++) {
            YearMonth ym = target.minusMonths(i);
            String partitionName = partitionName(ym.getYear(), ym.getMonthValue());
            String archiveName = archiveName(ym.getYear(), ym.getMonthValue());

            if (!partitionExists(partitionName)) {
                log.debug("[MsgLogArchive] 分区不存在,跳过: {}", partitionName);
                continue;
            }
            if (archiveTableExists(archiveName)) {
                log.warn("[MsgLogArchive] 归档表已存在,跳过 DETACH: {}", archiveName);
                continue;
            }

            try {
                // DETACH: PG 14+ 支持 CONCURRENTLY,不阻塞父表读写
                jdbcTemplate.execute(String.format(
                        "ALTER TABLE %s DETACH PARTITION %s%s",
                        PARENT_TABLE, partitionName,
                        supportsDetachConcurrently() ? " CONCURRENTLY" : ""));
                log.info("[MsgLogArchive] DETACH 成功: {}", partitionName);
            } catch (Exception e) {
                log.warn("[MsgLogArchive] DETACH 失败,跳过该分区: {} - {}", partitionName, e.getMessage());
                continue;
            }

            try {
                jdbcTemplate.execute(String.format(
                        "ALTER TABLE %s RENAME TO %s",
                        partitionName, archiveName));
                log.info("[MsgLogArchive] RENAME 成功: {} -> {}", partitionName, archiveName);
                archived.add(archiveName);
            } catch (DuplicateKeyException e) {
                log.warn("[MsgLogArchive] 归档表已存在,RENAME 失败: {}", archiveName);
            } catch (Exception e) {
                log.error("[MsgLogArchive] RENAME 失败: {} -> {} - {}",
                        partitionName, archiveName, e.getMessage(), e);
            }
        }
        return archived;
    }

    @Override
    public String ensurePartition(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        String partitionName = partitionName(year, month);
        if (partitionExists(partitionName)) {
            log.debug("[MsgLogArchive] 分区已存在,跳过: {}", partitionName);
            return null;
        }
        YearMonth start = ym;
        YearMonth end = ym.plusMonths(1);
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM ('%s') TO ('%s')",
                partitionName, PARENT_TABLE,
                start.atDay(1), end.atDay(1));
        jdbcTemplate.execute(sql);
        log.info("[MsgLogArchive] 创建分区: {} [{}, {})", partitionName, start.atDay(1), end.atDay(1));
        return partitionName;
    }

    /**
     * 检查分区是否存在。
     *
     * @param partitionName 分区名
     * @return true 表示存在
     */
    private boolean partitionExists(String partitionName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = CURRENT_SCHEMA() AND c.relname = ?",
                Integer.class, partitionName);
        return count != null && count > 0;
    }

    /**
     * 检查归档表是否存在。
     *
     * @param archiveName 归档表名
     * @return true 表示存在
     */
    private boolean archiveTableExists(String archiveName) {
        return partitionExists(archiveName);
    }

    /**
     * 判断 PostgreSQL 是否支持 DETACH CONCURRENTLY（PG 14+）。
     *
     * <p>缓存结果避免频繁查询版本。生产环境假定 PG 14+，单元测试可重写。
     *
     * @return true 表示支持
     */
    private boolean supportsDetachConcurrently() {
        try {
            Integer major = jdbcTemplate.queryForObject(
                    "SELECT split_part(version(), ' ', 2)::int", Integer.class);
            return major != null && major >= 14;
        } catch (Exception e) {
            log.warn("[MsgLogArchive] 获取 PG 版本失败,默认使用非 CONCURRENTLY DETACH: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成分区名: {@code pmis_msg_log_y2026m05}。
     *
     * @param year  年
     * @param month 月(1-12)
     * @return 分区名
     */
    private static String partitionName(int year, int month) {
        return String.format("%s%dm%02d", PARTITION_PREFIX, year, month);
    }

    /**
     * 生成归档表名: {@code pmis_msg_log_archive_202605}。
     *
     * @param year  年
     * @param month 月(1-12)
     * @return 归档表名
     */
    private static String archiveName(int year, int month) {
        return String.format("%s%04d%02d", ARCHIVE_PREFIX, year, month);
    }
}
