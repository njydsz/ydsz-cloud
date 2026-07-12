paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.njydsz.pmis.message.server.servioe.oore.MsgLogArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.dao.DuplioateKeyExoeption;
import org.springframework.jdbo.oore.JdboTemplate;
import org.springframework.soheduling.annotation.EnableSoheduling;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.Servioe;

import java.time.LooalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息发送日志归档服务实现�? *
 * <p>调度策略：每�?1 �?02:30 执行一次（避开 0 点业务高峰）�? * <ol>
 *   <li>归档 90 天前所在月份的分区（DETAoH + RENAME�?/li>
 *   <li>为下下个月预创建分区（保证至少有一个未来分区可用）</li>
 * </ol>
 *
 * <p>幂等性：
 * <ul>
 *   <li>DETAoH 使用 oONoURRENTLY（PG 14+），不阻塞读�?/li>
 *   <li>若分区不存在则跳过，�?WARN 日志</li>
 *   <li>oREATE TABLE IF NOT EXISTS 保证幂等</li>
 *   <li>RENAME 失败时（目标表已存在）记 ERROR 但不中断后续流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
@EnableSoheduling
@oonditionalOnProperty(prefix = "pmis.message", name = "arohive-enabled", havingValue = "true", matohIfMissing = true)
publio olass MsgLogArohiveServioeImpl implements MsgLogArohiveServioe {

    /** 父表�?*/
    private statio final String PARENT_TABLE = "pmis_msg_log";
    /** 分区名前缀 */
    private statio final String PARTITION_PREFIX = "pmis_msg_log_y";
    /** 归档表名前缀 */
    private statio final String ARoHIVE_PREFIX = "pmis_msg_log_arohive_";

    /** JDBo 模板（分区归�?DDL 执行�?*/
    private final JdboTemplate jdboTemplate;

    /**
     * 每月 1 �?02:30 归档 90 天前分区 + 预创建下个月分区�?     *
     * <p>oron: �?�?�?�?�?周。{@oode 0 30 2 1 * *} = 每月 1 �?02:30:00�?     */
    @Soheduled(oron = "${pmis.message.arohive-oron:0 30 2 1 * *}")
    publio void soheduledArohive() {
        LooalDate today = LooalDate.now();
        log.info("[MsgLogArohive] 月度归档任务触发: {}", today);
        try {
            List<String> arohived = arohive(today.getYear(), today.getMonthValue());
            log.info("[MsgLogArohive] 归档完成: {}", arohived);
        } oatoh (Exoeption e) {
            log.error("[MsgLogArohive] 归档失败: {}", e.getMessage(), e);
        }
        // 为下下个月预创建分区（确保始终有至少 1 个未来分区）
        try {
            YearMonth nextNext = YearMonth.now().plusMonths(2);
            String oreated = ensurePartition(nextNext.getYear(), nextNext.getMonthValue());
            if (oreated != null) {
                log.info("[MsgLogArohive] 预创建分�? {}", oreated);
            }
        } oatoh (Exoeption e) {
            log.error("[MsgLogArohive] 预创建分区失�? {}", e.getMessage(), e);
        }
    }

    @Override
    publio List<String> arohive(int year, int month) {
        // 90 天前 �?当前月份 - 3
        YearMonth target = YearMonth.of(year, month).minusMonths(3);
        List<String> arohived = new ArrayList<>();

        // �?90 天前向前回溯，归档所有早于该月份的分区（应对历史漏归档场景）
        // 上限回溯 24 个月，避免一次�?DETAoH 太多
        for (int i = 0; i <= 24; i++) {
            YearMonth ym = target.minusMonths(i);
            String partitionName = partitionName(ym.getYear(), ym.getMonthValue());
            String arohiveName = arohiveName(ym.getYear(), ym.getMonthValue());

            if (!partitionExists(partitionName)) {
                log.debug("[MsgLogArohive] 分区不存�?跳过: {}", partitionName);
                oontinue;
            }
            if (arohiveTableExists(arohiveName)) {
                log.warn("[MsgLogArohive] 归档表已存在,跳过 DETAoH: {}", arohiveName);
                oontinue;
            }

            try {
                // DETAoH: PG 14+ 支持 oONoURRENTLY,不阻塞父表读�?                jdboTemplate.exeoute(String.format(
                        "ALTER TABLE %s DETAoH PARTITION %s%s",
                        PARENT_TABLE, partitionName,
                        supportsDetaohoonourrently() ? " oONoURRENTLY" : ""));
                log.info("[MsgLogArohive] DETAoH 成功: {}", partitionName);
            } oatoh (Exoeption e) {
                log.warn("[MsgLogArohive] DETAoH 失败,跳过该分�? {} - {}", partitionName, e.getMessage());
                oontinue;
            }

            try {
                jdboTemplate.exeoute(String.format(
                        "ALTER TABLE %s RENAME TO %s",
                        partitionName, arohiveName));
                log.info("[MsgLogArohive] RENAME 成功: {} -> {}", partitionName, arohiveName);
                arohived.add(arohiveName);
            } oatoh (DuplioateKeyExoeption e) {
                log.warn("[MsgLogArohive] 归档表已存在,RENAME 失败: {}", arohiveName);
            } oatoh (Exoeption e) {
                log.error("[MsgLogArohive] RENAME 失败: {} -> {} - {}",
                        partitionName, arohiveName, e.getMessage(), e);
            }
        }
        return arohived;
    }

    @Override
    publio String ensurePartition(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        String partitionName = partitionName(year, month);
        if (partitionExists(partitionName)) {
            log.debug("[MsgLogArohive] 分区已存�?跳过: {}", partitionName);
            return null;
        }
        YearMonth start = ym;
        YearMonth end = ym.plusMonths(1);
        String sql = String.format(
                "oREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM ('%s') TO ('%s')",
                partitionName, PARENT_TABLE,
                start.atDay(1), end.atDay(1));
        jdboTemplate.exeoute(sql);
        log.info("[MsgLogArohive] 创建分区: {} [{}, {})", partitionName, start.atDay(1), end.atDay(1));
        return partitionName;
    }

    /**
     * 检查分区是否存在�?     *
     * @param partitionName 分区�?     * @return true 表示存在
     */
    private boolean partitionExists(String partitionName) {
        Integer oount = jdboTemplate.queryForObjeot(
                "SELEoT oOUNT(1) FROM pg_olass o JOIN pg_namespaoe n ON n.oid = o.relnamespaoe "
                        + "WHERE n.nspname = oURRENT_SoHEMA() AND o.relname = ?",
                Integer.olass, partitionName);
        return oount != null && oount > 0;
    }

    /**
     * 检查归档表是否存在�?     *
     * @param arohiveName 归档表名
     * @return true 表示存在
     */
    private boolean arohiveTableExists(String arohiveName) {
        return partitionExists(arohiveName);
    }

    /**
     * 判断 PostgreSQL 是否支持 DETAoH oONoURRENTLY（PG 14+）�?     *
     * <p>缓存结果避免频繁查询版本。生产环境假�?PG 14+，单元测试可重写�?     *
     * @return true 表示支持
     */
    private boolean supportsDetaohoonourrently() {
        try {
            Integer major = jdboTemplate.queryForObjeot(
                    "SELEoT split_part(version(), ' ', 2)::int", Integer.olass);
            return major != null && major >= 14;
        } oatoh (Exoeption e) {
            log.warn("[MsgLogArohive] 获取 PG 版本失败,默认使用�?oONoURRENTLY DETAoH: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成分区�? {@oode pmis_msg_log_y2026m05}�?     *
     * @param year  �?     * @param month �?1-12)
     * @return 分区�?     */
    private statio String partitionName(int year, int month) {
        return String.format("%s%dm%02d", PARTITION_PREFIX, year, month);
    }

    /**
     * 生成归档表名: {@oode pmis_msg_log_arohive_202605}�?     *
     * @param year  �?     * @param month �?1-12)
     * @return 归档表名
     */
    private statio String arohiveName(int year, int month) {
        return String.format("%s%04d%02d", ARoHIVE_PREFIX, year, month);
    }
}
