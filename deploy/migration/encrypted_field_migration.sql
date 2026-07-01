-- =============================================================================
-- EncryptedField 历史数据回填迁移脚本
-- 批次18 / P3-4
--
-- 背景:
--   1. 项目自 V1.0.0_013 起, 引入 @EncryptedField 字段级加密注解
--      (AES-256-GCM / SM4-GCM, 输出 base64(IV || ct+tag))。
--   2. 历史数据(部署初期)以明文形式落库, 需一次性回填到密文列,
--      并保留明文备份以支持回滚。
--   3. 由于 AES-GCM/SM4-GCM 需要 IV 随机化, SQL 端无法保证密文可重现,
--      故纯 SQL 脚本只负责:
--         (a) 准备备份表
--         (b) 准备密文列
--         (c) 切换读写路径
--      实际加密由 Java 端 EncryptedFieldMigrationService 配合本脚本完成。
--
-- 加密字段约定(以下示例以典型敏感字段为例, 实际表结构可能不同):
--   pmis_user_account.id_card     (身份证号, AES-GCM, 32 字节密钥)
--   pmis_user_account.phone       (手机号,   AES-GCM)
--   pmis_user_account.bank_card   (银行卡号, AES-GCM)
--   pmis_user_account.email       (邮箱,     AES-GCM)
--   pmis_user_account.address     (地址,     AES-GCM)
--
-- 使用方式:
--   psql -U pmis -d pmis -f deploy/migration/encrypted_field_migration.sql
--   # 然后运行 Java 迁移服务:
--   java -cp ydsz-pmis-common.jar com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli
--
-- 安全提示:
--   - 备份表 _plain 仅保留至回填验证完毕 (建议保留 30 天后由 ops 手动 drop)
--   - 切换密文列前必须确认明文备份完整
--   - 切换前必须停机或挂维护模式, 避免双写
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1) 幂等保护: 标记当前批次, 防止重复执行
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_migration_log (
    id              BIGSERIAL PRIMARY KEY,
    batch_code      VARCHAR(64)  NOT NULL,
    phase           VARCHAR(32)  NOT NULL,   -- PREPARE / BACKUP / ENCRYPT / SWITCH / CLEANUP
    table_name      VARCHAR(128),
    column_name     VARCHAR(128),
    affected_rows   BIGINT       DEFAULT 0,
    started_at      TIMESTAMP    DEFAULT NOW(),
    finished_at     TIMESTAMP,
    status          VARCHAR(16)  NOT NULL,   -- RUNNING / SUCCESS / FAILED / ROLLED_BACK
    remark          TEXT
);
CREATE INDEX IF NOT EXISTS idx_pmis_migration_log_batch ON pmis_migration_log(batch_code, phase);

INSERT INTO pmis_migration_log(batch_code, phase, status, remark)
VALUES ('V1.0.0_018_ENCRYPTED_FIELD', 'PREPARE', 'RUNNING',
        'P3-4 历史数据回填: 准备阶段 - 创建备份表 + 密文列');
-- 记录本次批次的 batch_id (取最新一条)
-- 仅用于本脚本内的 LOG 操作, 不影响业务

-- -----------------------------------------------------------------------------
-- 2) 准备明文备份表 (按需创建, 不覆盖已有)
-- -----------------------------------------------------------------------------
-- 备份命名约定: pmis_<原表名>_plain_backup_<batch>
-- 备份表结构与原表完全一致, 但额外冗余 batch_code + 备份时间

DO $$
DECLARE
    v_src regclass;
    v_backup text;
    v_batch text := 'V1.0.0_018_ENCRYPTED_FIELD';
BEGIN
    -- 2.1 pmis_user_account
    v_src := 'pmis_user_account'::regclass;
    v_backup := 'pmis_user_account_plain_backup_' || replace(v_batch, '.', '_');
    IF to_regclass(v_backup) IS NULL THEN
        EXECUTE format(
            'CREATE TABLE %I (LIKE %I INCLUDING ALL)',
            v_backup, v_src
        );
        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN pmis_migration_batch VARCHAR(64), '
            'ADD COLUMN pmis_migration_at TIMESTAMP DEFAULT NOW()',
            v_backup
        );
        RAISE NOTICE '[EncryptedField] 备份表 % 创建完成', v_backup;
    ELSE
        RAISE NOTICE '[EncryptedField] 备份表 % 已存在, 跳过创建', v_backup;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3) 准备密文列 (nullable, 不影响原 DML)
--    列命名约定: <原列名>_cipher
-- -----------------------------------------------------------------------------

-- 3.1 pmis_user_account: id_card / phone / email / bank_card / address
ALTER TABLE pmis_user_account
    ADD COLUMN IF NOT EXISTS id_card_cipher     TEXT,
    ADD COLUMN IF NOT EXISTS phone_cipher       TEXT,
    ADD COLUMN IF NOT EXISTS email_cipher       TEXT,
    ADD COLUMN IF NOT EXISTS bank_card_cipher   TEXT,
    ADD COLUMN IF NOT EXISTS address_cipher     TEXT;

-- 兼容字段可能为 BYTEA 的历史情况
DO $$
BEGIN
    -- 若已存在 BYTEA 列, 重命名为 _legacy (避免误用)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pmis_user_account' AND column_name = 'id_card_legacy'
    ) THEN
        EXECUTE 'ALTER TABLE pmis_user_account RENAME COLUMN id_card_legacy TO id_card_plain_dropped';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE '[EncryptedField] legacy 列处理跳过: %', SQLERRM;
END $$;

-- -----------------------------------------------------------------------------
-- 4) 标记 PREPARE 阶段完成
-- -----------------------------------------------------------------------------
UPDATE pmis_migration_log
SET status = 'SUCCESS', finished_at = NOW(),
    remark = '备份表 + 密文列准备完毕, 待 Java 端执行 ENCRYPT 阶段'
WHERE batch_code = 'V1.0.0_018_ENCRYPTED_FIELD' AND phase = 'PREPARE' AND status = 'RUNNING';

COMMIT;

-- 提示
\echo '======================================================================'
\echo '[EncryptedField] PREPARE 阶段完成'
\echo '  - pmis_migration_log 已记录'
\echo '  - 备份表 pmis_user_account_plain_backup_V1_0_0_018_ENCRYPTED_FIELD 已创建'
\echo '  - 密文列 id_card_cipher / phone_cipher / email_cipher /'
\echo '               bank_card_cipher / address_cipher 已添加'
\echo ''
\echo '  下一步: 运行 Java 端 ENCRYPT 阶段'
\echo '    java -cp ydsz-pmis-common.jar \'
\echo '      com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli \'
\echo '      --phase=ENCRYPT --batch=V1.0.0_018_ENCRYPTED_FIELD'
\echo '======================================================================'
