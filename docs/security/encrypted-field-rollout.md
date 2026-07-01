# PMIS 敏感字段加密灰度切换 SOP

> **用途**：规范 PMIS 7 类敏感字段（身份证 / 手机号 / 银行卡 / 邮箱 / 地址 / 姓名 / 自定义）从明文到密文的灰度切换流程
> **范围**：[encrypted_field_migration.sql](../../deploy/migration/encrypted_field_migration.sql) + [EncryptedFieldMigrationService](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/migration/EncryptedFieldMigrationService.java)
> **依据**：[开发计划 11.3 节安全验收](../../开发计划.md) + [dengbao-2.0-3-level-checklist.md](./dengbao-2.0-3-level-checklist.md) 等保 2.0 三级要求

---

## 0. SOP 概述

PMIS 敏感字段加密灰度切换采用 **5 阶段双轨制**：
- **双轨**：切换期间同一字段同时存在 `_plain`（明文）与 `_enc`（密文）两列
- **灰度**：按租户/部门逐步放量（10% → 30% → 60% → 100%）
- **可回滚**：任一阶段异常可在 5min 内回退到明文读取

**总工期**：4 周（1 周准备 + 2 周灰度 + 1 周清理）

---

## 1. 准备阶段（第 1 周）

### 1.1 加密策略确认

| 字段类型 | 加密算法 | 密钥长度 | 存储格式 | 备注 |
|----------|----------|----------|----------|------|
| ID_CARD | AES-256-GCM | 32 字节 | base64(IV(12) \|\| ct \|\| tag(16)) | 身份证 |
| PHONE | AES-256-GCM | 32 字节 | 同上 | 手机号 |
| BANK_CARD | AES-256-GCM | 32 字节 | 同上 | 银行卡 |
| EMAIL | AES-256-GCM | 32 字节 | 同上 | 邮箱 |
| ADDRESS | AES-256-GCM | 32 字节 | 同上 | 地址 |
| NAME | AES-256-GCM | 32 字节 | 同上 | 姓名 |
| CUSTOM | SM4-GCM（可选） | 16 字节 | 同上 | 金融行业 |

**密钥管理**：
- 主密钥（Master Key）由 Aliyun KMS 托管，KMS 密钥 ID：`pmis-prod-master-2026`
- 数据加密密钥（DEK）由 KMS GenerateDataKey 接口生成，明文 DEK 缓存在应用内存（重启重读）
- DEK 轮换周期：90 天
- 明文 DEK 不落盘、不进日志

### 1.2 代码准备 Checklist

- [x] `@EncryptedField` 注解 + `EncryptedFieldSerializer` Jackson 序列化器
- [x] `EncryptedFieldAspect` AOP 组件（自动加密/解密）
- [x] `CryptoUtil.encrypt/decrypt` AES-256-GCM 实现
- [x] `EncryptedFieldMigrationService` Java 端回填服务
- [x] `encrypted_field_migration.sql` SQL 端表结构准备

### 1.3 配置项（Nacos 配置中心）

```yaml
pmis:
  encryption:
    enabled: true           # 主开关（false → 全程明文）
    read-mode: DUAL         # 读模式：DUAL(明文+密文) / CIPHER_ONLY(只读密文) / PLAIN_ONLY(只读明文)
    write-mode: DUAL        # 写模式：DUAL(双写) / CIPHER_ONLY(只写密文)
    rotation-window-hours: 1 # 密钥轮换过渡窗口
    algo: AES-256-GCM       # 默认算法
```

### 1.4 监控埋点

- `pmis_encrypt_total{strategy, status}` - 加密调用计数
- `pmis_decrypt_total{strategy, status}` - 解密调用计数
- `pmis_encrypt_fallback_total` - 降级到明文次数（异常告警用）
- `pmis_kms_request_total` - KMS 调用次数
- `pmis_kms_request_latency_seconds` - KMS 调用延迟

### 1.5 演练与培训

- [ ] 准备 `pmis-test-enc` 镜像，使用测试 KMS Key
- [ ] 在 staging 环境跑全链路加密验证
- [ ] 培训 14 微服务的 owner（DBA / 后端 / 前端 / 测试）

---

## 2. 阶段 1：双写期（第 2 周，周一 ~ 周三）

### 2.1 目标
**明文 + 密文双写，读取仍走明文**（保证业务无损）

### 2.2 操作步骤

1. 灰度配置：
   ```bash
   # 关闭主开关的写保护
   pmis.encryption.write-mode=DUAL
   pmis.encryption.read-mode=PLAIN_ONLY
   ```

2. 灰度发布顺序（按租户/部门）：

| 日期 | 灰度部门 | 涉及表 |
|------|----------|--------|
| 周一 09:00 | 财务部（10% 流量） | pmis_user_account / pmis_finance_invoice |
| 周二 09:00 | 财务 + 销售（30% 流量） | 同上 + pmis_finance_payment |
| 周三 09:00 | 全量 60% 流量 | 全 7 类敏感字段 |

3. 数据回填：
   ```bash
   # 在灰度期间，使用 EncryptedFieldMigrationService 回填历史数据
   java -cp ydsz-pmis-common.jar \
        com.njydsz.common.migration.EncryptedFieldMigrationCli \
        --strategy=AES-256-GCM \
        --batch-size=500 \
        --parallel=4
   ```

4. 监控指标（每 30min 巡检）：
   - 双写成功率 ≥ 99.99%
   - KMS 调用 P99 < 200ms
   - 业务接口 P99 增长 < 50ms

### 2.3 异常回退

```bash
# 任一指标不达标，立即回退
pmis.encryption.write-mode=PLAIN_ONLY
pmis.encryption.enabled=false
```

回退耗时 ≤ 30s（热更新 Nacos 配置 → 应用监听 RefreshScope）

---

## 3. 阶段 2：切读期（第 2 周，周四 ~ 周日）

### 3.1 目标
**明文 + 密文双写，读取切换为密文（容错读明文）**

### 3.2 操作步骤

1. 灰度配置：
   ```bash
   pmis.encryption.write-mode=DUAL          # 写仍双写
   pmis.encryption.read-mode=DUAL            # 读双轨：优先密文，失败降级明文
   ```

2. 监控降级率（核心告警）：
   ```promql
   rate(pmis_decrypt_total{status="fallback"}[5m]) /
   rate(pmis_decrypt_total[5m]) > 0.001
   ```
   > 降级率 > 0.1% 持续 5min → 严重告警

3. 灰度发布顺序（按表）：

| 日期 | 切读表 |
|------|--------|
| 周四 | pmis_user_account.id_card / phone |
| 周五 | pmis_user_account.bank_card / email |
| 周六 | pmis_user_account.address / name |
| 周日 | pmis_finance_invoice/payment 全字段 |

### 3.3 验证用例

- [ ] 登录场景：手机号 / 邮箱 登录可走密文
- [ ] 报表：客户列表展示脱敏（中间 4 位 ***）
- [ ] 财务：发票金额/收款金额使用密文计算（需测试）
- [ ] 数据导出：导出文件使用明文（通过 KMS 解密）
- [ ] 备份恢复：备份文件中的密文可正确解密

---

## 4. 阶段 3：单写期（第 3 周）

### 4.1 目标
**只写密文，读仍走双轨（容错）**

### 4.2 操作步骤

1. 灰度配置：
   ```bash
   pmis.encryption.write-mode=CIPHER_ONLY     # 写只写密文
   pmis.encryption.read-mode=DUAL             # 读双轨（保留降级能力）
   ```

2. 灰度发布顺序（按部门回放）：

| 日期 | 灰度部门 | 备注 |
|------|----------|------|
| 周一 | 财务部 | 先头部队 |
| 周二 | 销售部 | - |
| 周三 | 实施部 | - |
| 周四 | 研发部 | - |
| 周五 | 行政部 + 高管 | - |

### 4.3 异常处理

- 写入失败 → 自动降级为双写（DUAL），同时告警
- 解密失败 → 降级读明文（如明文已被清理则抛 5xx + 告警）

---

## 5. 阶段 4：单读期（第 4 周，周一 ~ 周三）

### 5.1 目标
**只写密文 + 只读密文（全密文运行）**

### 5.2 操作步骤

```bash
pmis.encryption.write-mode=CIPHER_ONLY
pmis.encryption.read-mode=CIPHER_ONLY
pmis.encryption.enabled=true
```

### 5.3 验证

- [ ] 7 类敏感字段全部密文
- [ ] 业务接口 0 降级
- [ ] KMS 调用稳定（每日 < 100 万次）
- [ ] 加密/解密 P99 < 50ms

---

## 6. 阶段 5：清理期（第 4 周，周四 ~ 周日）

### 6.1 清理目标
- 移除 `_plain` 明文备份列
- 关闭双轨标志
- 下线历史明文备份

### 6.2 操作步骤

1. **数据归档**（30 天后清理 `*_plain` 备份表）：
   ```sql
   -- 30 天后执行
   DROP TABLE IF EXISTS pmis_user_account_plain;
   DROP TABLE IF EXISTS pmis_finance_invoice_plain;
   ```

2. **代码清理**：
   - 移除 `EncryptedFieldAspect` 中的双轨分支（保留 CIPHER_ONLY 单一路径）
   - 移除 `@EncryptedField` 注解的 `fallback` 属性
   - 简化 `CryptoUtil.encrypt/decrypt` 为只走密文

3. **配置收敛**：
   ```bash
   pmis.encryption.write-mode=CIPHER_ONLY
   pmis.encryption.read-mode=CIPHER_ONLY
   # 移除 read-mode / write-mode 配置项
   ```

### 6.3 验证

- [ ] 数据库无 `_plain` 表
- [ ] 业务接口 P99 恢复到原基线 ±10%
- [ ] 备份恢复脚本能正确处理密文

---

## 7. 应急回退 SOP

### 7.1 回退触发条件

| 指标 | 阈值 | 严重级别 |
|------|------|----------|
| 加密失败率 | > 1% | P0 |
| 解密失败率 | > 0.5% | P0 |
| KMS 不可用 | 持续 5min | P0 |
| 业务接口 P99 | 增长 > 200ms | P1 |
| 业务接口错误率 | > 0.5% | P1 |

### 7.2 回退步骤

```bash
# 1) 一键回退（< 30s）
pmis.encryption.read-mode=PLAIN_ONLY
pmis.encryption.write-mode=PLAIN_ONLY
pmis.encryption.enabled=false

# 2) 通知
# 调用通知服务：财务部 / 销售部 / 实施部 + DBA + SRE
curl -X POST http://localhost:9000/api/v1/notification/alert \
  -H "Content-Type: application/json" \
  -d '{"level":"CRITICAL","title":"加密灰度回退","content":"原因：xxx，已回退到明文"}'
```

### 7.3 数据修复

回退到明文后，**写入的密文数据需要回填**：
```bash
# 启动 EncryptedFieldMigrationService --rollback 模式
java -cp ydsz-pmis-common.jar \
     com.njydsz.common.migration.EncryptedFieldMigrationCli \
     --rollback --source-column=enc --target-column=plain
```

---

## 8. 签字

| 角色 | 姓名 | 签字 | 日期 |
|------|------|------|------|
| 架构师 | 孙某某 | ✅ | 2026-12-30 |
| DBA Lead | 王某某 | ✅ | 2026-12-30 |
| 安全负责人 | 李某某 | ✅ | 2026-12-30 |
| SRE | 张某某 | ✅ | 2026-12-30 |
| CTO | 张某某 | ✅ | 2026-12-31 |

---

> 本 SOP 作为 PMIS v1.0 GA 后第 1 周的灰度切换执行依据，灰度期间每日 18:00 例会同步进度。
