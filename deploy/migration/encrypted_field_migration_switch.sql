-- =============================================================================
-- EncryptedField 历史数据回填迁移脚本 - SWITCH 阶段
-- 批次18 / P3-4
--
-- 用途:
--   Java 端 ENCRYPT 阶段完成后, 验证明文备份完整 + 密文非空, 然后:
--     (1) 把明文列拷贝到备份表 (只拷贝 1 次, 幂等)
--     (2) 校验密文列非空比例
--     (3) 把密文列 rename 为正式列, 原明文列 rename 为 _plain (待清理)
--     (4) 业务层代码切换读取 *_cipher 列
--
-- 严格步骤:
--   1. 维护窗口挂起, 停止所有写入
--   2. psql -U pmis -d pmis -f encrypted_field_migration_switch.sql
--   3. 应用重启, 切到读取 *_cipher 列
--   4. 验证 30 天后, 手动 DROP 明文备份表
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1) 拷贝当前明文到备份表 (幂等: ON CONFLICT DO NOTHING)
-- -----------------------------------------------------------------------------
INSERT INTO pmis_user_account_plain_backup_V1_0_0_018_ENCRYPTED_FIELD
SELECT *, 'V1.0.0_018_ENCRYPTED_FIELD' AS pmis_migration_batch, NOW() AS pmis_migration_at
FROM pmis_user_account
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2) 校验密文非空比例
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_total BIGINT;
    v_id_card_enc BIGINT;
    v_phone_enc BIGINT;
    v_email_enc BIGINT;
    v_bank_card_enc BIGINT;
    v_address_enc BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_total FROM pmis_user_account;

    SELECT COUNT(*) INTO v_id_card_enc   FROM pmis_user_account WHERE id_card_cipher   IS NOT NULL AND id_card_cipher   <> '';
    SELECT COUNT(*) INTO v_phone_enc     FROM pmis_user_account WHERE phone_cipher     IS NOT NULL AND phone_cipher     <> '';
    SELECT COUNT(*) INTO v_email_enc     FROM pmis_user_account WHERE email_cipher     IS NOT NULL AND email_cipher     <> '';
    SELECT COUNT(*) INTO v_bank_card_enc FROM pmis_user_account WHERE bank_card_cipher IS NOT NULL AND bank_card_cipher <> '';
    SELECT COUNT(*) INTO v_address_enc   FROM pmis_user_account WHERE address_cipher   IS NOT NULL AND address_cipher   <> '';

    RAISE NOTICE '[EncryptedField] SWITCH 校验:';
    RAISE NOTICE '  总记录: %', v_total;
    RAISE NOTICE '  id_card 已加密: %', v_id_card_enc;
    RAISE NOTICE '  phone 已加密:   %', v_phone_enc;
    RAISE NOTICE '  email 已加密:   %', v_email_enc;
    RAISE NOTICE '  bank_card 已加密: %', v_bank_card_enc;
    RAISE NOTICE '  address 已加密: %', v_address_enc;

    IF v_total > 0 AND (v_id_card_enc = 0 AND v_phone_enc = 0) THEN
        RAISE EXCEPTION '[EncryptedField] SWITCH 失败: 密文列均为空, 请先运行 ENCRYPT 阶段';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3) 切换列名 (原子 rename, 无需 ALTER 锁)
--    原明文列: id_card / phone / email / bank_card / address -> *_plain
--    密文列: *_cipher -> 原列名
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    -- id_card
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='id_card'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='id_card_cipher'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN id_card TO id_card_plain;
        ALTER TABLE pmis_user_account RENAME COLUMN id_card_cipher TO id_card;
        RAISE NOTICE '[EncryptedField] id_card 已切换为密文列';
    END IF;

    -- phone
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='phone'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='phone_cipher'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN phone TO phone_plain;
        ALTER TABLE pmis_user_account RENAME COLUMN phone_cipher TO phone;
        RAISE NOTICE '[EncryptedField] phone 已切换为密文列';
    END IF;

    -- email
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='email'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='email_cipher'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN email TO email_plain;
        ALTER TABLE pmis_user_account RENAME COLUMN email_cipher TO email;
        RAISE NOTICE '[EncryptedField] email 已切换为密文列';
    END IF;

    -- bank_card
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='bank_card'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='bank_card_cipher'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN bank_card TO bank_card_plain;
        ALTER TABLE pmis_user_account RENAME COLUMN bank_card_cipher TO bank_card;
        RAISE NOTICE '[EncryptedField] bank_card 已切换为密文列';
    END IF;

    -- address
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='address'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='pmis_user_account' AND column_name='address_cipher'
    ) THEN
        ALTER TABLE pmis_user_account RENAME COLUMN address TO address_plain;
        ALTER TABLE pmis_user_account RENAME COLUMN address_cipher TO address;
        RAISE NOTICE '[EncryptedField] address 已切换为密文列';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 4) 记录 SWITCH 完成
-- -----------------------------------------------------------------------------
INSERT INTO pmis_migration_log(batch_code, phase, table_name, status, remark)
VALUES ('V1.0.0_018_ENCRYPTED_FIELD', 'SWITCH', 'pmis_user_account', 'SUCCESS',
        '列已切换, 业务层重启后从 *_cipher 列读取');

COMMIT;

\echo '======================================================================'
\echo '[EncryptedField] SWITCH 阶段完成'
\echo '  - 明文已备份到 pmis_user_account_plain_backup_V1_0_0_018_ENCRYPTED_FIELD'
\echo '  - 业务列已切换为密文 (id_card / phone / email / bank_card / address)'
\echo '  - 原明文列重命名为 *_plain, 30 天后手动清理'
\echo ''
\echo '  验证步骤:'
\echo '    SELECT id, id_card, phone FROM pmis_user_account LIMIT 5;'
\echo '    # 应当看到 base64 密文 (以 gAAAAA... 开头, 由 AES-GCM 输出)'
\echo '======================================================================'
