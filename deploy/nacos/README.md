# Nacos 配置中心

将各微服务配置通过 Nacos 集中管理，本目录约定 **互联网大厂标准** 的 DataId / Group / Namespace 三段式命名。

## 1. Namespace（命名空间）规划

| 环境 | Namespace ID | 用途 |
|------|--------------|------|
| dev | pmis-dev | 本地开发联调 |
| test | pmis-test | 测试环境 |
| staging | pmis-staging | 预发环境 |
| prod | pmis-prod | 生产环境 |

> 命名空间必须在 Nacos 控制台预先创建（`命名空间 > 新建命名空间`），并将其 ID 同步给 `application.yml` 中 `${NACOS_NAMESPACE}`。

## 2. Group 规范

| Group | 用途 |
|-------|------|
| PMIS_GROUP | 业务微服务配置（默认） |
| SEATA_GROUP | Seata 分布式事务（namespace 固定 `pmis`） |
| COMMON_GROUP | 公共组件（不推荐） |

## 3. DataId 命名规则

```
<spring.application.name>-<spring.profiles.active>.yaml
```

示例：

| 微服务 | DataId | 端口 |
|--------|--------|------|
| ydsz-pmis-gateway | ydsz-pmis-gateway-prod.yaml | 9000 |
| ydsz-pmis-auth | ydsz-pmis-auth-prod.yaml | 9001 |
| ydsz-pmis-user | ydsz-pmis-user-prod.yaml | 9002 |
| ydsz-pmis-notification | ydsz-pmis-notification-prod.yaml | 9013 |
| ydsz-pmis-workflow | ydsz-pmis-workflow-prod.yaml | 9014 |
| ydsz-pmis-project | ydsz-pmis-project-prod.yaml | 9015 |
| ydsz-pmis-execution | ydsz-pmis-execution-prod.yaml | 9016 |
| ydsz-pmis-agent | ydsz-pmis-agent-prod.yaml | 9017 |
| ydsz-pmis-config | ydsz-pmis-config-prod.yaml | 9018 |
| ydsz-pmis-file | ydsz-pmis-file-prod.yaml | 9019 |
| ydsz-pmis-audit | ydsz-pmis-audit-prod.yaml | 9020 |
| ydsz-pmis-message | ydsz-pmis-message-prod.yaml | 9021 |
| ydsz-pmis-scheduler | ydsz-pmis-scheduler-prod.yaml | 9022 |

## 4. 共享配置（推荐）

建议在每个 Namespace 下创建一份共享配置，避免各服务重复定义：

| DataId | Group | 内容 |
|--------|-------|------|
| pmis-common-dev.yaml | PMIS_GROUP | DB、Redis、MyBatis-Plus、Logging 等公共服务配置 |
| pmis-common-test.yaml | PMIS_GROUP | 同上（测试环境参数） |
| pmis-common-staging.yaml | PMIS_GROUP | 同上（预发环境参数） |
| pmis-common-prod.yaml | PMIS_GROUP | 同上（生产环境参数） |

各服务配置通过 `spring.cloud.nacos.config.extension-configs` 引入：

```yaml
spring:
  cloud:
    nacos:
      config:
        extension-configs:
          - dataId: pmis-common-prod.yaml
            group: PMIS_GROUP
            refresh: true
```

> 现阶段各服务配置已自包含 DB / Redis 等公共项，可以独立导入；后续可抽取为 `pmis-common-{env}.yaml` 以减少冗余。

## 5. 导入方式

### 5.1 控制台导入（推荐 - 首次）

1. 访问 `http://<nacos-host>:8848/nacos`，默认账号 `nacos / nacos`。
2. 顶部菜单 `命名空间` → `新建命名空间`，分别创建 `pmis-dev` / `pmis-test` / `pmis-staging` / `pmis-prod`。
3. 顶部菜单 `配置管理` → `配置列表` → 选中目标 Namespace → 右上角 `+`（导入配置）。
4. 每个服务在 `src/main/resources/` 下有 4 个 YAML（dev / test / staging / prod），逐个上传：
   - DataId：`<service-name>-<env>.yaml`
   - Group：`PMIS_GROUP`
   - 配置格式：`YAML`
   - 粘贴文件内容
5. 重复至所有服务 × 环境导入完成。

### 5.2 OpenAPI 批量导入（生产环境推荐）

Nacos 提供 OpenAPI 可批量导入：

```bash
# 通用导入脚本（伪代码，参考官方 API 文档）
curl -X POST "http://nacos-host:8848/nacos/v1/cs/configs" \
  -d "dataId=ydsz-pmis-user-prod.yaml" \
  -d "group=PMIS_GROUP" \
  -d "namespaceId=pmis-prod" \
  -d "type=yaml" \
  --data-urlencode "content@src/main/resources/ydsz-pmis-user-prod.yaml"
```

也可使用 `nacos-cli` / `nacos-sync` 等工具。

### 5.3 Git → Nacos 自动化（CI/CD）

通过 Jenkins / GitLab CI / ArgoCD 流水线：

1. 监听 `deploy/nacos/**/*.yaml` 变更
2. 调用 Nacos OpenAPI 推送到对应 Namespace
3. 触发 `Refresh` 或等待客户端长轮询

## 6. 占位符与变量

配置中所有 `${VAR}` 占位符通过以下两种方式注入：

### 6.1 容器环境变量（推荐）

在 `docker-compose.apps.yml` / Kubernetes Deployment 中注入：

```yaml
environment:
  DB_HOST: pmis-postgres
  DB_PORT: 5432
  DB_USER: pmis
  DB_PASSWORD: <vault-注入>
  REDIS_HOST: pmis-redis
  REDIS_PORT: 6379
  REDIS_PASSWORD: <vault-注入>
  NACOS_SERVER_ADDR: pmis-nacos:8848
  NACOS_NAMESPACE: pmis-prod
  PMIS_JWT_SECRET: <vault-注入>
  DASHSCOPE_API_KEY: <vault-注入>
```

### 6.2 配置中直接写值（不推荐，仅 dev 适用）

仅在本地 dev 环境配置中保留默认值（如 `127.0.0.1:8848`），prod 配置一律通过环境变量或 Nacos 控制台编辑注入。

## 7. 安全规范

1. **绝不提交** 任何明文密钥、数据库密码到 Git 仓库
2. dev 配置保留默认值仅用于本地启动；test/staging/prod 必须由运维在 Nacos 控制台注入密钥
3. 启用 Nacos 鉴权（`nacos.core.auth.enabled=true`），生产环境禁止开启免鉴权模式
4. 配合 KMS / Vault 统一管理密钥，配置文件只保留 `${KEY}` 占位符
5. 审计：开启 Nacos 操作日志（`nacos.core.auth.server.tokenttl=18000`），记录所有配置变更

## 8. 灰度与回滚

- **灰度发布**：使用 Nacos 灰度发布功能（`beta` / `gray`），先下发到指定 IP
- **回滚**：保留历史版本（`版本回滚`），一键回退到上一个稳定版本
- **监控**：通过 `pmis-*` 开头的 metrics tag 区分服务，结合 `actuator/configprops` 检查实际生效配置

## 9. 验证生效

服务启动后通过以下端点检查：

```bash
# 查看生效的配置属性
curl http://<service-host>:<port>/actuator/configprops

# 查看 Nacos 健康状态
curl http://<service-host>:<port>/actuator/health/nacos

# 查看 Refresh 状态
curl http://<service-host>:<port>/actuator/refresh
```

或在 Nacos 控制台 `服务管理 → 服务列表` 看到服务注册即表示发现 + 配置 + 鉴权全链路正常。

## 10. 附录 - 模块清单

| 序号 | 模块 | 端口 | DataId 前缀 |
|------|------|------|-------------|
| 1 | ydsz-pmis-gateway | 9000 | ydsz-pmis-gateway- |
| 2 | ydsz-pmis-auth | 9001 | ydsz-pmis-auth- |
| 3 | ydsz-pmis-user | 9002 | ydsz-pmis-user- |
| 4 | ydsz-pmis-notification | 9013 | ydsz-pmis-notification- |
| 5 | ydsz-pmis-workflow | 9014 | ydsz-pmis-workflow- |
| 6 | ydsz-pmis-project | 9015 | ydsz-pmis-project- |
| 7 | ydsz-pmis-execution | 9016 | ydsz-pmis-execution- |
| 8 | ydsz-pmis-agent | 9017 | ydsz-pmis-agent- |
| 9 | ydsz-pmis-config | 9018 | ydsz-pmis-config- |
| 10 | ydsz-pmis-file | 9019 | ydsz-pmis-file- |
| 11 | ydsz-pmis-audit | 9020 | ydsz-pmis-audit- |
| 12 | ydsz-pmis-message | 9021 | ydsz-pmis-message- |
| 13 | ydsz-pmis-scheduler | 9022 | ydsz-pmis-scheduler- |
