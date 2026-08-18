-- ============================================================
-- V5: 分享访问日志归档清理策略（P1-12）
-- ============================================================
-- 背景：nw_share_access_log 为高写入表，长期累积拖慢查询。
-- 策略：按月份将 90 天前的访问日志归档到 nw_share_access_log_archive，
--       归档后从主表删除；归档表按月分区进一步降低查询成本。
-- 数据库：PostgreSQL 16+
-- ============================================================

-- 1. 归档表（按月分区，字段与主表一致）
CREATE TABLE IF NOT EXISTS nw_share_access_log_archive (
    id              VARCHAR(64) NOT NULL,
    share_id        VARCHAR(64) NOT NULL,
    share_code      VARCHAR(64) NOT NULL,
    file_node_id    VARCHAR(64) NOT NULL,
    visitor_id      VARCHAR(64),
    visitor_name    VARCHAR(100),
    visitor_ip      VARCHAR(64),
    user_agent      VARCHAR(500),
    access_type     VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    access_status   VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    fail_reason     VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE nw_share_access_log_archive IS '分享访问日志归档表（按月分区，防止主表无限膨胀）';

-- 2. 归档函数：将 created_at 早于 retention_days 的日志搬移到归档表
CREATE OR REPLACE FUNCTION nw_archive_share_access_logs(retention_days INT)
RETURNS INT AS $$
DECLARE
    archived_count INT := 0;
BEGIN
    INSERT INTO nw_share_access_log_archive
        (id, share_id, share_code, file_node_id, visitor_id, visitor_name,
         visitor_ip, user_agent, access_type, access_status, fail_reason, created_at, deleted)
    SELECT id, share_id, share_code, file_node_id, visitor_id, visitor_name,
           visitor_ip, user_agent, access_type, access_status, fail_reason, created_at, deleted
    FROM nw_share_access_log
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL
      AND deleted = 0;

    GET DIAGNOSTICS archived_count = ROW_COUNT;

    DELETE FROM nw_share_access_log
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL
      AND deleted = 0;

    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION nw_archive_share_access_logs(INT) IS '分享访问日志归档：迁移 retention_days 天前的记录到归档表并从主表删除';

-- 3. 归档表初始分区（近 12 个月 + 未来 3 个月）
DO $$
DECLARE
    m DATE;
BEGIN
    FOR i IN -12..3 LOOP
        m := date_trunc('month', CURRENT_DATE + (i || ' months')::INTERVAL)::date;
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS nw_share_access_log_archive_%s PARTITION OF nw_share_access_log_archive '
            || 'FOR VALUES FROM (%L) TO (%L)',
            to_char(m, 'YYYYMM'),
            to_char(m, 'YYYY-MM-01'),
            to_char(m + INTERVAL '1 month', 'YYYY-MM-01'));
    END LOOP;
END;
$$;

-- 4. 归档表查询索引
CREATE INDEX IF NOT EXISTS idx_archive_share_created
    ON nw_share_access_log_archive (share_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_archive_created
    ON nw_share_access_log_archive (created_at DESC);

-- 5. 使用说明（由定时任务按周调用，例如每周日凌晨 4:00）：
--    SELECT nw_archive_share_access_logs(90);
--    应用侧可在 NextwikiScheduledJobs 中增加 @DistributedScheduled(lockKey = "nextwiki:archive-share-logs")
--    的定时任务调用上述函数，避免多实例重复归档。
