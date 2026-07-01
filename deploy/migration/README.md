# Legacy 数据迁移与对账

> 批次 21 / P1 11.4 — 端到端遗留系统数据迁移与月度对账

## 目录

```
deploy/migration/
├── README.md                     # 本文件
├── migration.conf                # 通用配置 (DSN/容差/通知)
├── legacy-extract.sh             # 1) 数据抽取 (legacy → staging JSONB)
├── legacy-transform.sh           # 2) 数据转换 (staging JSONB → staging row)
├── legacy-load.sh                # 3) 数据加载 (staging → 业务表)
├── legacy-accuracy-verify.sh     # 4) 准确性校验 (三方一致性)
├── finance-coa-mapping.sh        # 5) 财务科目映射
├── monthly-reconcile-job.sh      # 6) 月度对账任务
├── encrypted_field_migration.sql # 字段级加密历史回填 (批次 18, 旧)
├── encrypted_field_migration_rollback.sql
├── encrypted_field_migration_switch.sql
└── cron.d/
    └── pmis-migration            # cron 注册: 月度对账 + COA 校验
```

## 端到端流程

```
┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌──────────────┐
│ Legacy DB   │ → │ pmis_stage  │ → │ pmis_*      │ → │ 业务表        │
│ (ERP/Fin/HR)│   │ (JSONB 原样) │   │ _staging    │   │ pmis_*        │
└─────────────┘   └─────────────┘   └─────────────┘   └──────────────┘
   legacy-           legacy-           legacy-          legacy-
   extract.sh        transform.sh      load.sh          accuracy-verify.sh
                                                  ↓
                                          finance-coa-mapping.sh
                                                  ↓
                                          monthly-reconcile-job.sh (每月 1 日)
```

## 快速使用

```bash
# 加载配置
cd deploy/migration
source migration.conf

# 1) 抽取 (默认 dry-run 模拟数据; 配 --dsn 后真实抽取)
./legacy-extract.sh --source=erp --period=2026-06 --dsn="${LEGACY_ERP_DSN}"
./legacy-extract.sh --source=finance --period=2026-06 --dsn="${LEGACY_FINANCE_DSN}"
./legacy-extract.sh --source=hr --period=2026-06 --dsn="${LEGACY_HR_DSN}"

# 2) 转换 (默认 dry-run, 加 --commit 才落库到 staging)
./legacy-transform.sh --source=erp --period=2026-06

# 3) 加载 (默认 dry-run, 加 --commit 才写业务表)
./legacy-load.sh --source=erp --period=2026-06 --commit

# 4) 准确性校验 (staging vs 业务表; 容差 0.01)
./legacy-accuracy-verify.sh --source=erp --period=2026-06

# 5) 财务科目映射 (默认 dry-run, --commit 写入 pmis_finance_coa)
./finance-coa-mapping.sh

# 6) 月度对账 (每月 1 日 03:00 自动)
./monthly-reconcile-job.sh --period=2026-06 --notify
```

## 状态机

```
[EXTRACT] --成功--> [TRANSFORM] --成功--> [LOAD] --成功--> [VERIFY]
    |                    |                    |                 |
    v                    v                    v                 v
  FAILED              FAILED               FAILED            FAIL
                                                            ↓
                                                     [RECONCILE] (每月)
```

任一阶段记录到 `pmis_legacy_*_log` 表，可通过：

```sql
-- 抽取历史
SELECT * FROM pmis_legacy_extract_log ORDER BY started_at DESC LIMIT 20;
-- 转换历史
SELECT * FROM pmis_legacy_transform_log ORDER BY started_at DESC LIMIT 20;
-- 加载历史
SELECT * FROM pmis_legacy_load_log ORDER BY started_at DESC LIMIT 20;
-- 校验历史
SELECT * FROM pmis_legacy_verify_log ORDER BY started_at DESC LIMIT 20;
-- 对账历史
SELECT * FROM pmis_reconcile_log ORDER BY started_at DESC LIMIT 20;
```

## 月度对账规则 (5 类)

| # | 类型 | 业务规则 | 异常阈值 |
|---|------|----------|----------|
| 1 | 发票 vs 收款 | 单张发票已收款 = 发票金额 | abs(diff) > 0.01 |
| 2 | 合同 vs 发票 | 累计开票 <= 合同金额 | 超过即异常 |
| 3 | 项目 vs 预算 | 预算总额 <= 合同金额 | 超过即异常 |
| 4 | 工时 vs 工资 | 工资 ≈ 工时 × 平均时薪 | 偏差 > 50 元/小时 |
| 5 | COA 借贷 | 借方合计 = 贷方合计 | abs(diff) > 0.01 |

## cron 注册

```bash
# 月度对账 - 每月 1 日 03:00
0 3 1 * *  cd /opt/pmis/deploy/migration && ./monthly-reconcile-job.sh --period=$(date -d "last month" +\%Y-\%m) --notify >> /var/log/pmis/reconcile.log 2>&1

# COA 映射校验 - 每月 5 日 04:00 (在 P1 月度关账后执行)
0 4 5 * *  cd /opt/pmis/deploy/migration && ./finance-coa-mapping.sh --report-only >> /var/log/pmis/coa-mapping.log 2>&1

# staging 数据清理 - 每月 15 日 02:00 (清理 30 天前的 staging)
0 2 15 * *  cd /opt/pmis/deploy/migration && ./cleanup-staging.sh --older-than-days=30 >> /var/log/pmis/cleanup.log 2>&1
```

## 关键安全约束

1. **DSN 不入版本库** — `migration.conf` 中 `LEGACY_*_DSN` 通过 Ansible Vault 注入
2. **维护窗口** — TRANSFORM / LOAD 阶段必须在维护窗口执行, 避免双写
3. **幂等保证** — 所有脚本都支持 `--resume` 跳过已处理批次
4. **审计追溯** — 6 张 *_log 表记录全量操作历史, 满足等保 2.0 三级审计要求
5. **dry-run 优先** — 默认都是 dry-run, 必须显式 `--commit` 才落库
6. **回滚机制** — 业务表保留 `_legacy_id` / `_migration_batch`, 必要时按 batch_code DELETE

## 失败处理 SOP

| 阶段 | 失败现象 | 处置 |
|------|----------|------|
| EXTRACT | 抽取行数 < 预期 | 1) 检查源库网络 2) 重跑 `--resume` 3) 联系源系统 DBA |
| TRANSFORM | staging 字段类型不匹配 | 1) 修正 transform_one 中字段映射 2) 清空 staging 重新转换 |
| LOAD | 唯一键冲突 | 1) 已有数据, 用 `--resume` 跳过 2) 真冲突则人工核对 |
| VERIFY | mismatch > 阈值 | 1) 抽取 TRANSFORM 日志对比 2) 修正后重跑 |
| RECONCILE | 借贷不平衡 | 1) 通知财务手工调账 2) 修正凭证后重跑 |

## 与其他批次的关系

| 依赖批次 | 接口 |
|----------|------|
| 批次 18 字段加密 | `pmis_*_cipher` 列名兼容 staging |
| 批次 17 等保 2.0 | `pmis_legacy_*_log` 满足审计日志 6 个月保留 |
| 批次 19 变更管理 | 业务表 `_legacy_id` 字段排除在变更审计之外 |
| 批次 20 双算利润 | 对账时与 dual_rate_profit 视图交叉验证 |

## 升级路径

1. **V1.0 → V2.0**: 增加 Kafka 流式抽取, 支持近实时增量
2. **V2.0 → V3.0**: 引入 Debezium CDC, 监听遗留库 binlog
3. **V3.0 → V4.0**: 全量自动化 + AI 异常检测 (驱动对账差异分类)
