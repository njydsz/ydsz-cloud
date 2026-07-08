# ydsz-pmis-system

> 系统基础服务（System Foundation）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9002**（按构建顺序 3/8） |
| **服务名** | `ydsz-pmis-system` |
| **构建顺序** | 3/8 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |

## 核心职责

本模块是 PMIS 的**系统级基础服务**，承担**横切关注点**。

| 业务域 | 说明 |
|---|---|
| **文件管理** | MinIO 对象存储、文件上传/下载/预览、分片上传、秒传 |
| **系统配置** | 参数配置（`pmis_config`）、数据字典（`pmis_dict`） |
| **登录审计** | 接收 `LoginAuditEvent`，异步落库 `pmis_login_audit` |
| **操作审计** | 接收 `OperationLogEvent`，异步落库 `pmis_operation_log` |
| **数据导出审计** | `DataExportAuditEvent` → `pmis_data_export_audit` |

> ⚠️ **重要**：消息通知 / 模板 / 渠道已迁移到 [ydsz-pmis-message](ydsz-pmis-message/README.md)（端口 9004）。
> 本服务**不再**提供消息发送能力。

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/file/upload` / `/file/download` | 文件上传下载 |
| `/file/multipart/init` / `/complete` | 分片上传 |
| `/file/presign` | 预签名 URL（私有 Bucket） |
| `/config` | 系统参数 |
| `/dict` / `/dict/item` | 数据字典 |
| `/audit/login` | 登录审计（查询接口） |
| `/audit/operation` | 操作审计 |
| `/audit/data-export` | 数据导出审计 |

## 数据库表设计

本模块在 `deploy/sql/V1.0.0.sql` 中持有 **12 张表**，聚焦"横切关注点"：文件、配置、审计、跨服务公共表。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **文件管理** | `pmis_file` | 文件元信息（bucket、key、大小、MIME、上传者、md5） |
| **系统配置** | `pmis_config` | 系统参数（key-value，支持租户维度） |
| | `pmis_dict_version` | 字典版本（多环境字典版本控制） |
| **审计日志** | `pmis_login_audit` | 登录审计（与 userinfo 共享物理表） |
| | `pmis_operation_log` | 操作审计（DDL+CRUD 统一审计，支持按月分区） |
| | `pmis_operation_log_default` | 操作审计默认分区 |
| | `pmis_operation_log_yYYYYmMM` | 操作审计月度分区模板（按需创建） |
| | `pmis_data_export_audit` | 数据导出审计（导出人、表、行数、用途） |
| | `pmis_sensitive_operation` | 敏感操作审计（金额/合同/权限变更） |
| **跨服务公共** | `pmis_report_subscription` | 报表订阅（用户×报表×频率） |
| | `pmis_export_record` | 异步导出记录（异步导出到 MinIO） |
| | `pmis_meta_schema_version` | DB Schema 版本号（启动校验） |

> **分区说明**：`pmis_operation_log` 为 PostgreSQL 范围分区表（按月分区），历史月份可走 `pg_partman` 或手动 `DETACH` 归档。
> **脱敏约束**：`pmis_sensitive_operation`、`pmis_data_export_audit` 写入时自动 `SensitiveSerializer` 脱敏。

## 启动顺序

依赖 `common` + `nacos`，**应在 `gateway` 之后**启动，可与 `userinfo` / `project` 并行。

## 目录结构

```
ydsz-pmis-system/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/pmis/system/
    │   ├── SystemApplication.java
    │   ├── controller/
    │   │   ├── FileController.java
    │   │   ├── ConfigController.java
    │   │   ├── DictController.java
    │   │   └── AuditController.java
    │   ├── service/
    │   │   ├── FileService.java
    │   │   ├── MinioStorageService.java
    │   │   └── AuditQueryService.java
    │   ├── listener/
    │   │   ├── LoginAuditListener.java   # @Async
    │   │   ├── OperationLogListener.java  # @Async
    │   │   └── DataExportAuditListener.java
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── mapper/
    │   │   ├── ConfigMapper.xml
    │   │   ├── FileMapper.xml
    │   │   └── LoginAuditMapper.xml
    │   └── config/            # 原 nacos-config（已重命名）
    │       ├── ydsz-pmis-system-dev.yaml
    │       ├── ydsz-pmis-system-sit.yaml
    │       └── ydsz-pmis-system-uat.yaml
    └── test/
```

## 配置文件

**MinIO 配置**（必需）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MINIO_ENDPOINT` | `http://127.0.0.1:9100` | MinIO API 地址 |
| `MINIO_ACCESS_KEY` | `minioadmin` | 访问 Key |
| `MINIO_SECRET_KEY` | `minioadmin` | 密钥 |
| `MINIO_BUCKET` | `pmis` | 默认 Bucket |

**其他环境变量**：与 common 共享配置一致（DB / Redis）。

## 启动

```bash
# 1. 启动 MinIO（推荐 Docker）
docker run -d --name pmis-minio \
  -p 9100:9000 -p 9101:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v minio-data:/data \
  quay.io/minio/minio server /data --console-address ":9001"

# 2. 启动 system
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-system spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-system -am test
```

## Feign 接口

被以下 Feign 客户端调用（位于 `ydsz-pmis-common`）：

- `ConfigClient` → `/config` / `/dict`

## 常见问题

### Q1：文件上传报 "bucket does not exist"

需要先在 MinIO 控制台（`http://127.0.0.1:9101`）创建 Bucket `pmis`，
或在 `MinioStorageService.init()` 中添加自动创建逻辑。

### Q2：审计日志延迟

通过 `@Async` + `OperationLogEvent` 异步落库。若发现日志丢失，检查：
1. `application.yml` 中 `@EnableAsync` 是否开启
2. `OperationLogListener` 是否在 `META-INF/spring.factories` 或 `@ComponentScan` 范围内

---

> 任何新增审计类型请使用 `ApplicationEventPublisher` 发布事件，由对应 Listener 异步落库，**不要**在业务事务内同步写审计表。
