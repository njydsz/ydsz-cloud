# EncryptedField 历史数据回填迁移指南

> 批次18 / P3-4 — `@EncryptedField` 字段级加密历史数据迁移

## 背景

`@EncryptedField` 注解（见 `ydsz-pmis-common/.../sensitive/EncryptedField.java`）启用 AES-256-GCM / SM4-GCM 字段级加密。运行时通过 `EncryptedFieldSerializer` 在 Jackson 序列化时自动加密。

历史数据以**明文**落库（`id_card` / `phone` / `email` / `bank_card` / `address` 等），本批次提供端到端迁移流程，将明文回填为密文并保留可回滚的明文备份。

## 阶段

| Phase | 工具 | 描述 |
|-------|------|------|
| PREPARE | `encrypted_field_migration.sql` | 创建 `pmis_migration_log` + 明文备份表 + 密文列 |
| ENCRYPT | `EncryptedFieldMigrationCli` (Java) | 扫描明文 → AES-GCM 加密 → 写入 `*_cipher` 列 |
| VERIFY | `EncryptedFieldMigrationCli --phase=VERIFY` | 解密 `*_cipher` 与备份表对照 |
| SWITCH | `encrypted_field_migration_switch.sql` | 拷贝明文备份 → 重命名列 → 业务层切读密文 |
| CLEANUP | 30 天后手工执行 | DROP 明文备份表 + DROP 明文列 |

## 快速使用

```bash
# 1) 准备 (DDL, 仅创建表和列, 不动数据)
psql -U pmis -d pmis -f deploy/migration/encrypted_field_migration.sql

# 2) 加密 (Java 端读取明文, AES-GCM 加密后写 *_cipher)
java -cp ydsz-pmis-common.jar \
  com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli \
  --phase=ENCRYPT \
  --batch=V1.0.0_018_ENCRYPTED_FIELD \
  --key=ENC(AES256,...base64 32字节密钥...) \
  --batchSize=500

# 3) 校验 (解密 *_cipher, 与原明文对比)
java -cp ydsz-pmis-common.jar \
  com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli \
  --phase=VERIFY \
  --batch=V1.0.0_018_ENCRYPTED_FIELD

# 4) 切换 (拷贝明文备份, 重命名列, 业务切读密文)
psql -U pmis -d pmis -f deploy/migration/encrypted_field_migration_switch.sql

# 5) 30 天后, 手工清理
psql -U pmis -d pmis <<'SQL'
DROP TABLE IF EXISTS pmis_user_account_plain_backup_V1_0_0_018_ENCRYPTED_FIELD;
ALTER TABLE pmis_user_account DROP COLUMN IF EXISTS id_card_plain;
ALTER TABLE pmis_user_account DROP COLUMN IF EXISTS phone_plain;
ALTER TABLE pmis_user_account DROP COLUMN IF EXISTS email_plain;
ALTER TABLE pmis_user_account DROP COLUMN IF EXISTS bank_card_plain;
ALTER TABLE pmis_user_account DROP COLUMN IF EXISTS address_plain;
SQL
```

## 回滚

若 SWITCH 后发现问题（解密异常 / 性能回退 / 上游解析错误）：

```bash
psql -U pmis -d pmis -f deploy/migration/encrypted_field_migration_rollback.sql
```

回滚完成后：
- 列名恢复为明文
- 明文数据从备份表还原
- 备份表保留（供后续诊断）
- 应用代码无需变更（仍读 `id_card` / `phone` 等原列名）

## 安全提示

1. **维护窗口**：PREPARE / ENCRYPT / SWITCH / ROLLBACK 阶段必须在维护窗口执行，避免双写
2. **密钥管理**：32 字节 AES 密钥通过 Nacos `pmis.crypto.aes-key` 注入，**禁止**写入 SQL / 配置文件
3. **备份保留**：明文备份表保留 30 天（满足监管要求），到时由运维手工清理
4. **审计追溯**：`pmis_migration_log` 记录所有阶段（PREPARE / BACKUP / ENCRYPT / SWITCH / ROLLED_BACK），用于等保 2.0 审计
5. **范围限制**：本批次只覆盖 `pmis_user_account`，其他含敏感字段的表（`pmis_customer` / `pmis_employee` 等）按需扩展

## 字段映射表

| 原列 | 密文列 | 加密策略 | 备注 |
|------|--------|----------|------|
| `id_card` | `id_card_cipher` | AES-GCM, keyRef=pmis.crypto.aes-key | 身份证号 |
| `phone` | `phone_cipher` | AES-GCM, keyRef=pmis.crypto.aes-key | 手机号 |
| `email` | `email_cipher` | AES-GCM, keyRef=pmis.crypto.aes-key | 邮箱 |
| `bank_card` | `bank_card_cipher` | AES-GCM, keyRef=pmis.crypto.aes-key | 银行卡号 |
| `address` | `address_cipher` | AES-GCM, keyRef=pmis.crypto.aes-key | 地址 |

> 实际列名以 `pmis_user_account` 表结构为准。若表结构无上述字段，对应 DDL 语句自动跳过（`ADD COLUMN IF NOT EXISTS`）。

## 状态机

```
[PREPARE] --成功--> [ENCRYPT] --成功--> [VERIFY] --成功--> [SWITCH] --成功--> [CLEANUP]
    |                  |                    |                  |
    v                  v                    v                  v
  FAILED           FAILED               FAILED             ROLLBACK
```

任一阶段失败均记录到 `pmis_migration_log` 表的 `status` 字段，可通过：

```sql
SELECT batch_code, phase, status, affected_rows, started_at, finished_at, remark
FROM pmis_migration_log
WHERE batch_code = 'V1.0.0_018_ENCRYPTED_FIELD'
ORDER BY started_at;
```

查询历史执行情况。
