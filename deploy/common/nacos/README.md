# Nacos 共享配置说明

本目录存放所有微服务共用的 Nacos 配置文件，通过 `spring.cloud.nacos.config.shared-configs` 引入。

## 配置文件

| 文件 | 作用 |
| --- | --- |
| `ydsz-common.yaml` | 全集群共享配置（数据源、Redis、Feign、日志、密钥等） |

导入方式参考 `deploy/ubuntu/scripts/import-nacos-config.sh`。

## 密钥配置（P2-9 轻量级 KMS）

YDSZ 提供统一的密钥管理抽象（`SecretProvider`），业务代码通过 `SecretManager` 获取密钥。
密钥来源由 `ydsz.kms.provider` 决定，支持以下三种方式：

### 方式一：环境变量注入（推荐生产）

通过环境变量注入密钥，环境变量名转换规则：密钥标识 → `YDSZ_SECRETS_` 前缀 + 大写 + 点号转下划线。

| 密钥标识 | 环境变量名 |
| --- | --- |
| `db.password` | `YDSZ_SECRETS_DB_PASSWORD` |
| `redis.password` | `YDSZ_SECRETS_REDIS_PASSWORD` |
| `jwt.secret` | `YDSZ_SECRETS_JWT_SECRET` |

环境变量优先级最高，覆盖 Nacos 配置中的同名密钥。

### 方式二：Nacos 配置（开发环境）

在 `ydsz-common.yaml` 中直接配置 `ydsz.kms.secrets.*`：

```yaml
ydsz:
  kms:
    provider: environment
    secrets:
      db.password: ${DB_PASSWORD:}
      redis.password: ${REDIS_PASSWORD:}
      jwt.secret: ${YDSZ_JWT_SECRET:}
```

> 开发环境可直接配置明文，生产环境务必通过环境变量注入或使用 ENC() 加密。

### 方式三：Jasypt ENC() 加密（生产推荐）

将 `ydsz.kms.provider` 设为 `jasypt`，在 `ydsz.kms.secrets.*` 中配置 ENC() 密文，
运行时通过 Jasypt 自动解密（复用现有 `jasypt-spring-boot-starter` 配置）：

```yaml
ydsz:
  kms:
    provider: jasypt
    secrets:
      db.password: ENC(加密后的密码串)
      redis.password: ENC(加密后的密码串)
      jwt.secret: ENC(加密后的密钥串)
```

主密码通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入，不写入配置文件。

### 未来扩展：HashiCorp Vault / 阿里云 KMS

当前预留 `ydsz.kms.vault` 配置结构与 `SecretProvider` 接口扩展点，
后续生产环境规模化后可接入 Vault 或阿里云 KMS，无需改动业务代码。

## 生产环境建议

1. **数据库/Redis 密码**：使用环境变量注入或 ENC() 加密，禁止明文
2. **JWT 密钥**：使用环境变量注入，密钥长度至少 32 字节（HS256）
3. **Jasypt 主密码**：通过 `JASYPT_ENCRYPTOR_PASSWORD` 环境变量注入，不提交到代码仓库
4. **Druid 监控台**：生产环境关闭（`DRUID_STAT_ENABLED=false`）
