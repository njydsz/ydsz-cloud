# ydsz.json Nacos 共享配置基线

> 本文档定义应上载到 Nacos `ydsz-common.yaml` 共享配置中的 `ydsz.json` 统一基线。
> 创建日期：2026-08-03
> 关联文档：docs/ydsz-common-json-integration-analysis.md P1-3

## 基线内容

以下配置应写入 Nacos `ydsz-common.yaml`（所有服务共同的 JSON 行为）：

```yaml
ydsz:
  json:
    # 基础序列化配置
    enabled: true
    date-format: yyyy-MM-dd HH:mm:ss
    naming-strategy: SNAKE_CASE
    write-nulls: false
    # 安全性兜底
    safe-mode: true
    # 监控埋点
    monitoring-enabled: true
```

## 各服务差异化配置（保留在本地 application.yml）

| 配置项 | 默认值 | 用途 | 涉及服务 |
|---|---|---|---|
| `warmup-classes` | 空 | ASM 预热高频序列化 Bean | workflow/literule/message/system/agent/gateway/cronjob |
| `use-big-decimal` | false | 金额精度模式 | project/workflow |
| `streaming-enabled` | false | 大 JSON 流式输出 | workflow/gateway |

## 实施说明

1. **步骤 1**：将上述基线写入 Nacos 各环境的 `ydsz-common.yaml`（dev/test/prod）
2. **步骤 2**：删除各服务本地 `application.yml` 中与基线重复的配置项（date-format / naming-strategy / enabled / write-nulls / safe-mode / monitoring-enabled）
3. **步骤 3**：仅保留服务特有项（warmup-classes / use-big-decimal / streaming-enabled）
4. **验证**：重启后 9 个服务的 JSON 行为一致，异常可通过 Nacos 配置热刷新统一下发修复

## 迁移前状态

| 服务 | enabled | date-format | naming | write-nulls | monitoring | safe-mode | use-big-decimal | streaming | warmup | 去重后保留 |
|---|---|---|---|---|---|---|---|---|---|---|
| agent | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | ✅ | warmup |
| gateway | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | ✅ | ✅ | streaming, warmup |
| literule | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | ✅ | use-big-decimal, warmup |
| message | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | ✅ | warmup |
| nextwiki | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | ✅ | warmup |
| system | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | — | (无) |
| userinfo | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | — | (无) |
| project | — | — | — | — | — | — | ✅ | — | — | use-big-decimal |
| workflow | — | — | — | — | — | — | ✅ | ✅ | ✅ | use-big-decimal, streaming, warmup |
| cronjob | ✅ | — | — | — | ✅ | — | — | — | — | (无) |
