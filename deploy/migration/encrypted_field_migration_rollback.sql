-- =============================================================================
-- EncryptedField 回填迁移 - 回滚脚本
-- 批次18 / P3-4
--
-- 用途:
--   当 SWITCH 阶段出现数据问题 (解密失败、密文错位等), 通过此脚本:
--     (1) 把列名切换回明文
--     (2) 从备份表恢复原明文数据
--     (3) 标记 ROLLED_BACK 状态
--
-- 严格步骤:
--   1. 维护窗口挂起, 停止应用
--   2. psql -U pmis -d pmis -f encrypted_field_migration_rollback.sql
--   3. 应用重启, 业务代码切回明文
--   4. 修复问题后重新执行 ENCRYPT -> SWITCH
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1) 切换列名回明文 (逆向 rename)
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    -- id_card
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='id_card'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='id_card_plain'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN id_card TO id_card_cipher;
        ALTER TABLE pmis_user_account RENAME COLUMN id_card_plain TO id_card;
        RAISE NOTICE '[EncryptedField] id_card 已回滚为明文列';
    END IF;

    -- phone
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='phone'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='phone_plain'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN phone TO phone_cipher;
        ALTER TABLE pmis_user_account RENAME COLUMN phone_plain TO phone;
        RAISE NOTICE '[EncryptedField] phone 已回滚为明文列';
    END IF;

    -- email
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='email'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='email_plain'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN email TO email_cipher;
        ALTER TABLE pmis_user_account RENAME COLUMN email_plain TO email;
        RAISE NOTICE '[EncryptedField] email 已回滚为明文列';
    END IF;

    -- bank_card
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='bank_card'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='bank_card_plain'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN bank_card TO bank_card_cipher;
        ALTER TABLE pmis_user_account RENAME COLUMN bank_card_plain TO bank_card;
        RAISE NOTICE '[EncryptedField] bank_card 已回滚为明文列';
    END IF;

    -- address
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='address'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='address_plain'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN address TO address_cipher;
        ALTER TABLE pmis_user_account RENAME COLUMN address_plain TO address;
        RAISE NOTICE '[EncryptedField] address 已回滚为明文列';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2) 从备份表恢复明文数据 (按 id 匹配, 幂等)
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_backup text := 'pmis_user_account_plain_backup_V1_0_0_018_ENCRYPTED_FIELD';
    v_exists_regclass regclass;
BEGIN
    v_exists_regclass := to_regclass(v_backup);
    IF v_exists_regclass IS NULL THEN
        RAISE NOTICE '[EncryptedField] 备份表 % 不存在, 跳过数据恢复', v_backup;
        RETURN;
    END IF;

    -- id_card
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='id_card'
    ) THEN
        EXECUTE format(
            'UPDATE pmis_user_account t SET id_card = b.id_card '
            'FROM %I b WHERE t.id = b.id',
            v_backup
        );
        RAISE NOTICE '[EncryptedField] id_card 明文已从备份恢复';
    END IF;

    -- phone
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='phone'
    ) THEN
        EXECUTE format(
            'UPDATE pmis_user_account t SET phone = b.phone '
            'FROM %I b WHERE t.id = b.id',
            v_backup
        );
        RAISE NOTICE '[EncryptedField] phone 明文已从备份恢复';
    END IF;

    -- email
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='email'
    ) THEN
        EXECUTE format(
            'UPDATE pmis_user_account t SET email = b.email '
            'FROM %I b WHERE t.id = b.id',
            v_backup
        );
        RAISE NOTICE '[EncryptedField] email 明文已从备份恢复';
    END IF;

    -- bank_card
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='bank_card'
    ) THEN
        EXECUTE format(
            'UPDATE pmis_user_account t SET bank_card = b.bank_card '
            'FROM %I b WHERE t.id = b.id',
            v_backup
        );
        RAISE NOTICE '[EncryptedField] bank_card 明文已从备份恢复';
    END IF;

    -- address
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='address'
    ) THEN
        EXECUTE format(
            'UPDATE pmis_user_account t SET address = b.address '
            'FROM %I b WHERE t.id = b.id',
            v_backup
        );
        RAISE NOTICE '[EncryptedField] address 明文已从备份恢复';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3) 记录 ROLLED_BACK
-- -----------------------------------------------------------------------------
INSERT INTO pmis_migration_log(batch_code, phase, table_name, status, remark)
VALUES ('V1.0.0_018_ENCRYPTED_FIELD', 'ROLLBACK', 'pmis_user_account', 'SUCCESS',
        '回滚完成, 列名已恢复, 明文已从备份还原');

COMMIT;

\echo '======================================================================'
\echo '[EncryptedField] ROLLBACK 完成'
\echo '  - 列名已恢复为明文 (id_card / phone / email / bank_card / address)'
\echo '  - 明文数据已从备份还原'
\echo '  - 备份表 pmis_user_account_plain_backup_V1_0_0_018_ENCRYPTED_FIELD 保留'
\echo '  - 修复问题后, 可重新执行 ENCRYPT + SWITCH'
\echo '======================================================================'
