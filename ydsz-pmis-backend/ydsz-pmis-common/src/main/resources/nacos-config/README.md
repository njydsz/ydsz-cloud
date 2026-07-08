# ydsz-pmis-common · Nacos 配置模板目录

## 用途

本目录集中存放**所有微服务共用的 Nacos 共享配置**模板，与 `ydsz-pmis-common` 模块同源，
业务变更只需修改一处，deploy 脚本会自动同步到 Nacos 各环境。

## 目录约定

```
ydsz-pmis-common/src/main/resources/nacos-config/
├── ydsz-pmis-common.yaml          # 所有服务共用的共享配置（数据源/Redis/MP/SpringDoc 等）
├── ydsz-pmis-gateway-dev.yaml     # gateway 服务的 dev 环境配置（可选覆盖）
├── ydsz-pmis-userinfo-dev.yaml
├── ydsz-pmis-system-dev.yaml
├── ydsz-pmis-project-dev.yaml
├── ydsz-pmis-message-dev.yaml
├── ydsz-pmis-cronjob-dev.yaml
├── ydsz-pmis-workflow-dev.yaml
└── ydsz-pmis-agent-dev.yaml
```

> 当前版本只提供共享配置 `ydsz-pmis-common.yaml`。各服务的环境特定配置
> （`ydsz-pmis-{service}-{profile}.yaml`）按需新增。

## 共享配置说明

### `ydsz-pmis-common.yaml`

被 `bootstrap.yml` 通过 `spring.cloud.nacos.config.shared-configs` 引用：

```yaml
spring:
  cloud:
    nacos:
      config:
        shared-configs:
          - data-id: ydsz-pmis-common.yaml
            group: ${spring.profiles.active}   # dev / sit / uat / prod
            refresh: true
```

包含内容：

| 模块 | 覆盖项 |
|---|---|
| 数据源 | PostgreSQL + Druid + 动态数据源（master/slave） |
| 缓存 | Redis + Lettuce 池 + Spring Cache |
| 持久层 | MyBatis-Plus 全局配置（逻辑删除、ID 策略、字段映射） |
| 调用 | OpenFeign 超时 + Sentinel 熔断 + Resilience4j 重试 |
| API 文档 | SpringDoc + Knife4j 共享 |
| 日志 | Logback 控制台 Pattern + Level |
| 业务 | JWT / IP 白名单 / KMS / Jasypt 加密 |

## 部署到 Nacos

```bash
# 1. 共享配置（一次部署，全部环境）
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev
# 一次性导入 dev / sit / uat / prod 四个环境
for env in dev sit uat prod; do
  ./deploy/ubuntu/scripts/import-nacos-config.sh pmis $env
done
```

Windows 等价命令：

```powershell
# PowerShell
deploy\windows\scripts\import-nacos-config.bat pmis dev
```

> **重要**：脚本现在优先从 `ydsz-pmis-common/src/main/resources/nacos-config/` 读取配置，
> 向后兼容 `deploy/common/nacos/`。建议团队统一以 common 模块为唯一来源。

## 单一来源原则

所有 7 个微服务的公共配置，**只能**在本目录维护。修改流程：

1. 编辑 `ydsz-pmis-common.yaml` 模板；
2. 本地 `mvn test` 验证；
3. 提交 PR（Code Review 必查"是否新增/修改了重复的 datasource/redis 配置"）；
4. CI 通过后，通过 `import-nacos-config.sh` 同步到 Nacos；
5. 通知所有服务实例 `@RefreshScope` 刷新（默认已开启 `refresh: true`）。

## 验证

Nacos 控制台 → 配置管理 → 命名空间 `pmis` → 应能看到 `ydsz-pmis-common.yaml` 4 个 Group（dev/sit/uat/prod）。
