# ydsz-common-file

YDSZ 统一文件存储框架 — 7 种存储平台（Local / OSS / MinIO / S3 / COS / OBS / Qiniu）、分片上传、断点续传、文件去重（秒传）、文件类型安全检测、病毒扫描接口、生命周期管理、上传并发保护、重试、指标采集、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 56 |

## 核心能力

### 存储平台

| 类 | 说明 |
|---|---|
| `LocalStorage` | 本地文件存储 |
| `OssStorage` | 阿里云 OSS |
| `MinioStorage` | MinIO 对象存储 |
| `S3Storage` | AWS S3 兼容 |
| `CosStorage` | 腾讯云 COS |
| `ObsStorage` | 华为云 OBS |
| `QiniuStorage` | 七牛云 |
| `StorageType` | 存储类型枚举 |

### 统一接口

| 接口 / 类 | 说明 |
|---|---|
| `IFileStorage` | 文件存储接口（上传 / 下载 / 删除 / 列表） |
| `AbstractFileStorage` | 存储抽象基类 |
| `IFileStorageProvider` / `DefaultStorageFactory` | 存储提供者 / 工厂 |
| `FileUploader` | 文件上传器 |
| `FileDownloader` | 文件下载器 |
| `FileManager` | 文件管理器 |
| `StorageRetryHelper` | 存储操作重试助手（指数退避） |
| `UploadConcurrencyGuard` | 上传并发保护（防同文件并发分片冲突） |

### 分片上传与断点续传

| 类 | 说明 |
|---|---|
| `CheckpointService` / `DefaultCheckpointService` | 断点续传检查点服务 |
| `CheckpointStore` | 检查点存储接口 |
| `RedisCheckpointStore` / `LocalCheckpointStore` | Redis / 本地实现 |
| `DelegatingCheckpointStore` | 委托存储 |
| `MultipartContextStore` | 分片上下文存储 |
| `RedisMultipartContextStore` / `InMemoryMultipartContextStore` | Redis / 内存实现 |
| `DelegatingMultipartContextStore` | 委托存储 |
| `ChunkedUploadResult` | 分片上传结果 |

### 文件去重（秒传）

| 类 | 说明 |
|---|---|
| `FileDedupService` | 文件去重服务（SHA-256 哈希 → 引用创建） |

### 文件类型安全

| 类 | 说明 |
|---|---|
| `FileTypeDetector` | 文件类型检测器 |
| `FileTypeValidator` | 文件类型校验器 |
| `MagicNumberRegistry` | Magic Number 注册表 |
| `FileValidationException` / `FileExceptionCode` | 校验异常 |

### 病毒扫描

| 类 | 说明 |
|---|---|
| `VirusScanner` | 病毒扫描接口（`scan` / `isAvailable`） |
| `NoOpVirusScanner` | 默认 NoOp 实现（直接返回 CLEAN，**仅用于开发 / 测试**） |

> **生产环境警示**：默认装配的 `NoOpVirusScanner` 不会真正扫描病毒，仅占位返回 CLEAN。生产环境必须替换为真实实现（如 ClamAV），见 P0-8 任务。

### 生命周期管理

| 类 | 说明 |
|---|---|
| `FileLifecycleManager` | 文件生命周期管理（过期 / 归档 / 清理） |
| `FileLifecycleProperties` | 生命周期配置属性 |

### 可观测性

| 类 | 说明 |
|---|---|
| `FileMetrics` | 文件指标采集（上传 / 下载 QPS、耗时、存储用量） |
| `FileHealthIndicator` | 健康检查（存储连通性） |

### 领域模型

| 类 | 说明 |
|---|---|
| `FileStorage` | 文件存储实体 |
| `ObjectMetadata` | 对象元数据 |
| `ListObjectsResult` | 对象列表结果 |
| `DirectoryTree` | 目录树 |
| `PolicyResult` | 策略结果 |
| `UploadCheckpoint` | 上传检查点 |
| `BatchDeleteResult` | 批量删除结果 |

### 回调

| 类 | 说明 |
|---|---|
| `UploadProgressListener` | 上传进度回调 |

### 开关注解

| 注解 | 说明 |
|---|---|
| `@EnableYdszFile` | 文件模块自动装配入口 |

## 配置项

```yaml
ydsz:
  file:
    storage-type: minio            # local / oss / minio / s3 / cos / obs / qiniu
    upload:
      max-size: 100MB              # 单文件最大大小
      chunk-size: 5MB              # 分片大小
      allowed-extensions: [pdf,doc,docx,xls,xlsx,ppt,pptx,jpg,png,zip]
      blocked-extensions: [exe,bat,sh,js]
    dedup:
      enabled: true                # 秒传开关
    lifecycle:
      enabled: true
      temp-expire: 7d              # 临时文件过期
    virus-scan:
      enabled: false               # 默认关闭，开启需引入真实 VirusScanner Bean
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `FileConfiguration` | 总是激活 |
| `FileProperties` / `FileUploadProperties` / `FileLifecycleProperties` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-file</artifactId>
</dependency>
```
