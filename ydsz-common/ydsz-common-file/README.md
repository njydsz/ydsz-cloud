# ydsz-common-file

> 统一文件存储公共模块（L5 业务服务层）

提供 7 种存储平台（Local / OSS / MinIO / S3 / COS / OBS / Qiniu）的统一抽象、分片上传、断点续传、文件去重（秒传）、Magic Number 文件类型校验、病毒扫描接口、生命周期管理、上传并发保护、指数退避重试、Micrometer 指标采集与 Actuator 健康检查等开箱即用能力，是所有业务模块文件存取的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多存储后端统一抽象、分片上传、断点续传、秒传、病毒扫描、生命周期管理、健康检查等能力 |
| **依赖** | common-core、common-exception、common-util；可选依赖 spring-boot-actuator、micrometer-core、spring-data-redis、common-redis |
| **版本** | 1.0.0 |

## 核心能力

### 1. 存储平台抽象

| 类 | 说明 |
|---|---|
| `IFileStorage` | 文件存储统一接口，内含上传/下载/管理全部能力；内含 `PartInfo` record |
| `AbstractFileStorage` | 存储抽象基类，封装 bucketName 解析、路径穿越防护、分片校验、进度回调、并发锁、去重、病毒扫描、重试、指标采集等公共逻辑 |
| `IFileStorageProvider` | 存储提供者接口，`getStorage()` 返回当前存储实现 |
| `DefaultStorageFactory` | 默认工厂实现，按 `ydsz.file.type` 创建对应存储实例并单例缓存；支持 SPI 注册自定义存储类型 |
| `StorageType` | 存储类型常量类（LOCAL / ALIYUN / MINIO / AWS_S3 / QINIU / TENCENT_COS / HUAWEI_OBS） |
| `LocalStorage` | 本地文件存储实现 |
| `OssStorage` | 阿里云 OSS 实现 |
| `MinioStorage` | MinIO 对象存储实现 |
| `S3Storage` | AWS S3 兼容存储实现（兼容 Ceph、R2 等） |
| `CosStorage` | 腾讯云 COS 实现 |
| `ObsStorage` | 华为云 OBS 实现 |
| `QiniuStorage` | 七牛云存储实现 |

### 2. 上传能力

| 类 | 说明 |
|---|---|
| `UploadProgressListener` | 上传进度回调（onStart / onProgress / onSuccess / onFailure） |
| `ChunkedUploadResult` | 分片上传初始化结果（含 uploadId） |
| `PolicyResult` | 前端直传凭证（Policy / Signature） |
| `UploadCheckpoint` | 断点续传检查点（含 fileMd5、已上传分片列表） |

### 3. 下载能力

| 类 | 说明 |
|---|---|
| `ObjectMetadata` | 对象元信息（size / contentType / eTag / lastModified） |

### 4. 管理能力

| 类 | 说明 |
|---|---|
| `BatchDeleteResult` | 批量删除结果（成功列表 + 失败映射） |
| `ListObjectsResult` | 列举结果（对象列表 + nextCursor） |
| `DirectoryTree` | 目录树结构 |
| `FileStorage` | 文件存储实体（含 fileName / suffix / size / mimeType / 类型标记） |

### 5. 分片上传与断点续传

| 类 | 说明 |
|---|---|
| `CheckpointService` | 检查点业务接口（保存 / 加载 / 删除 / 校验恢复 / MD5 累积计算） |
| `DefaultCheckpointService` | 默认实现，封装序列化与 listParts 回调 |
| `CheckpointStore` | 检查点存储接口 |
| `RedisCheckpointStore` | Redis 检查点存储（多实例共享） |
| `LocalCheckpointStore` | 本地文件检查点存储（降级方案） |
| `DelegatingCheckpointStore` | 委托存储，优先 Redis 失败时降级本地 |
| `MultipartContextStore` | 分片上传上下文存储接口（uploadId → bucket/object/partChunkNames） |
| `RedisMultipartContextStore` | Redis 分片上下文存储（多实例共享） |
| `InMemoryMultipartContextStore` | 内存分片上下文存储（降级方案） |
| `DelegatingMultipartContextStore` | 委托存储，优先 Redis 失败时降级内存 |
| `UploadConcurrencyGuard` | 并发保护器，基于 Redis 防同文件并发上传冲突（REJECT / WAIT 两种策略） |

### 6. 文件去重（秒传）

| 类 | 说明 |
|---|---|
| `FileDedupService` | 基于 SHA-256 + 文件大小双重校验的秒传服务；通过 Redis 存储 `file:dedup:hash:{size}:{sha256}` → 已存在 URL 映射，30 天过期 |

### 7. 文件类型安全

| 类 | 说明 |
|---|---|
| `FileTypeDetector` | 文件类型检测器（MIME Type 推断） |
| `FileTypeValidator` | 文件类型校验器，支持后缀白名单 + Magic Number 双重校验，可由 `ydsz.file.check-magic-number` 关闭 |
| `MagicNumberRegistry` | Magic Number 注册表 |
| `FileValidationException` | 校验异常 |
| `FileExceptionCode` | 文件模块错误码枚举 |

### 8. 病毒扫描

| 类 | 说明 |
|---|---|
| `VirusScanner` | 病毒扫描接口（`scan` / `isAvailable`），返回 CLEAN / INFECTED / ERROR 三态结果 |
| `NoOpVirusScanner` | 默认空操作实现，所有文件视为 CLEAN |

> **生产环境警示**：默认装配的 `NoOpVirusScanner` 不会真正扫描病毒，仅占位返回 CLEAN。生产环境必须实现 `VirusScanner` 接口注册为 Spring Bean（如对接 ClamAV / ICAP），自动替换默认实现。

### 9. 生命周期管理

| 类 | 说明 |
|---|---|
| `FileLifecycleManager` | 基于 `@Scheduled` + Cron 的过期文件清理，按路径前缀匹配规则；支持 dryRun 模拟执行与手动触发 |
| `FileLifecycleProperties` | 生命周期配置（cron / bucket / rules / dryRun），每条规则含 prefix / maxAgeDays / action |

### 10. 重试与可观测性

| 类 | 说明 |
|---|---|
| `StorageRetryHelper` | 存储操作重试助手，指数退避；仅重试系统/网络异常，`BusinessException` 直接抛出 |
| `FileMetrics` | Micrometer 指标采集（上传/下载/删除计数与耗时、秒传命中/未命中、病毒检测命中、按错误码分组的上传/下载错误） |
| `FileHealthIndicator` | Actuator 健康检查（详见健康检查章节） |

### 11. 自动配置与开关

| 类 | 说明 |
|---|---|
| `FileConfiguration` | Spring Boot 自动配置入口，注册全部 Bean，`@EnableScheduling` 开启定时任务，每小时清理过期分片上下文 |
| `FileProperties` | 文件存储主配置属性（`ydsz.file.*`） |
| `FileUploadProperties` | 分片上传配置属性（`ydsz.file.upload.*`） |
| `FileLifecycleProperties` | 生命周期配置属性（`ydsz.file.lifecycle.*`） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-file</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  file:
    enabled: true
    type: minio                      # local / minio / oss / aliyun / s3 / aws-s3 / cos / tencent-cos / qiniu / obs / huawei-obs
    endpoint: http://localhost:9000
    access-key: ${MINIO_ACCESS_KEY}  # 建议环境变量注入
    secret-key: ${MINIO_SECRET_KEY}
    bucket: ydsz-files
    domain: https://files.example.com
```

### 2.1 配置基线与 Nacos 共享配置

基础设施级配置（endpoint / bucket / domain / 存储类型）建议在 Nacos 共享配置 `ydsz-common.yaml` 中统一定义，所有业务模块继承而无需各自维护：

```yaml
# Nacos DataId: ydsz-common.yaml（Infrastructure Baseline）
ydsz:
  file:
    type: minio
    endpoint: ${MINIO_ENDPOINT:http://minio.ydsz.svc:9000}
    bucket: ydsz-files
    domain: https://files.njydsz.com
    max-file-size: 104857600
    part-size: 5242880
    check-magic-number: true
    concurrency-control:
      enabled: true
    health-check-interval-seconds: 30
```

业务模块仅在需要覆盖差异化项时才在本地 yml 中添加 `ydsz.file.*` 配置，如：

```yaml
# 业务模块本地仅覆盖差异化项
ydsz:
  file:
    lifecycle:
      enabled: true          # 此模块需要自动清理临时文件
      rules:
        - prefix: "import/"  # 导入临时文件 24h 过期
          max-age-days: 1
```

> **注意**：在本地配置文件中声明 `ydsz.file.enabled=false` 可以关闭整个文件模块的自动装配，适用于不需要文件处理的开发/测试模块。

### 3. 启用模块

`FileConfiguration` 通过 Spring Boot 自动装配机制注册（`@AutoConfiguration` + `@ConditionalOnProperty`），无需额外注解即可使用。

## 配置项

### `ydsz.file.*`（FileProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.file.enabled` | true | 是否启用文件存储模块 |
| `ydsz.file.type` | `minio` | 存储类型：local / minio / oss / aliyun / s3 / aws-s3 / qiniu / cos / tencent-cos / obs / huawei-obs |
| `ydsz.file.endpoint` | - | 存储服务 endpoint |
| `ydsz.file.access-key` | - | 访问密钥（建议环境变量注入） |
| `ydsz.file.secret-key` | - | 秘密密钥（建议环境变量注入） |
| `ydsz.file.bucket` | - | 默认存储桶名称 |
| `ydsz.file.domain` | - | 文件访问域名（CDN 或 Nginx 代理地址） |
| `ydsz.file.region` | - | 区域信息（部分云存储需要） |
| `ydsz.file.allowed-suffixes` | `[]`（空表示允许全部） | 允许上传的文件后缀白名单 |
| `ydsz.file.max-file-size` | `104857600`（100MB） | 单文件最大大小（字节） |
| `ydsz.file.max-request-size` | `104857600`（100MB） | 单次请求最大大小（字节） |
| `ydsz.file.connection-timeout` | `30000`（30s） | 连接超时（毫秒） |
| `ydsz.file.socket-timeout` | `60000`（60s） | Socket 超时（毫秒） |
| `ydsz.file.max-connections` | `100` | HTTP 连接池最大连接数 |
| `ydsz.file.retry-count` | `3` | 存储操作失败重试次数（0-10） |
| `ydsz.file.temporary-signature-expiry` | `3600`（1h） | 私有 URL 过期时间（秒） |
| `ydsz.file.part-size` | `5242880`（5MB） | 分片大小（字节，与 S3 协议对齐） |
| `ydsz.file.checkpoint-dir` | 系统临时目录 | 断点续传检查点目录 |
| `ydsz.file.check-magic-number` | true | 是否启用 Magic Number 文件头校验 |
| `ydsz.file.concurrency-control.enabled` | true | 是否启用上传并发保护 |

### `ydsz.file.upload.*`（FileUploadProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.file.upload.chunk-md5-check` | false | 是否启用分片 MD5 校验（启用后每次分片计算 MD5，合并时校验整体 MD5） |

### `ydsz.file.lifecycle.*`（FileLifecycleProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.file.lifecycle.enabled` | false | 是否启用文件生命周期清理 |
| `ydsz.file.lifecycle.cron` | `0 0 2 * * ?` | 定时清理 Cron 表达式（默认每天凌晨 2 点） |
| `ydsz.file.lifecycle.bucket` | - | 目标存储桶（为空时使用默认桶） |
| `ydsz.file.lifecycle.dry-run` | false | 是否仅模拟执行（true 时只打印日志不实际删除） |
| `ydsz.file.lifecycle.rules[].prefix` | - | 文件路径前缀 |
| `ydsz.file.lifecycle.rules[].max-age-days` | - | 最大保留天数 |
| `ydsz.file.lifecycle.rules[].action` | `delete` | 到期动作（默认 delete） |

## 使用示例

### 1. 普通文件上传

```java
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.domain.FileStorage;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {

    private final IFileStorage fileStorage;

    public AvatarService(IFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public String uploadAvatar(MultipartFile file) {
        // bucketName 传 null 使用默认配置
        FileStorage result = fileStorage.upload(null, "avatar/" + file.getOriginalFilename(), file);
        return result.getUrl();
    }
}
```

### 2. 大文件分片上传 + 断点续传

```java
import com.njydsz.common.file.domain.UploadCheckpoint;
import com.njydsz.common.file.domain.ChunkedUploadResult;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LargeFileService {

    private final IFileStorage fileStorage;

    public LargeFileService(IFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    /** 初始化断点续传任务 */
    public UploadCheckpoint initUpload(String objectName, MultipartFile file) {
        return fileStorage.initChunkedUploadWithCheckpoint(null, objectName, file);
    }

    /** 恢复上传：仅上传未完成分片 */
    public String resume(UploadCheckpoint checkpoint) {
        return fileStorage.resumeChunkedUpload(checkpoint, null).getUrl();
    }
}
```

### 3. 文件下载（支持 Range 断点续传）

```java
import jakarta.servlet.http.HttpServletResponse;

@GetMapping("/download/{objectName}")
public void download(@PathVariable String objectName, HttpServletResponse response) {
    // 完整下载
    fileStorage.download(null, objectName, response);
}

@GetMapping(value = "/stream/{objectName}")
public void stream(@PathVariable String objectName,
                   @RequestHeader(value = "Range", required = false) String range,
                   HttpServletResponse response) {
    // 范围下载（offset=null 表示从 0 开始，length=null 表示读到末尾）
    fileStorage.download(null, objectName, response, 0L, 1024L * 1024L);
}
```

### 4. 文件生命周期清理配置

```yaml
ydsz:
  file:
    lifecycle:
      enabled: true
      cron: "0 0 2 * * ?"            # 每天凌晨 2 点执行
      dry-run: false                 # 生产建议先 true 试运行
      rules:
        - prefix: "temp/"
          max-age-days: 7
          action: delete
        - prefix: "logs/"
          max-age-days: 30
          action: delete
        - prefix: "archive/"
          max-age-days: 365
          action: delete
```

### 5. 自定义病毒扫描实现

```java
import com.njydsz.common.file.virus.VirusScanner;
import org.springframework.stereotype.Component;
import java.io.InputStream;

@Component
public class ClamAvVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(InputStream inputStream, String fileName) {
        // 对接 ClamAV / ICAP 实际扫描逻辑
        return ScanResult.CLEAN;
    }

    @Override
    public boolean isAvailable() {
        return true;  // 扫描引擎可用
    }
}
```

注册为 Spring Bean 后，`FileConfiguration` 通过 `@ConditionalOnMissingBean` 检测到已有实现，自动跳过 `NoOpVirusScanner` 装配。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `IFileStorage` | 文件存储统一抽象，实现后即可对接新的存储后端 | 框架内置 7 种实现（Local/Oss/Minio/S3/Cos/Obs/Qiniu），业务可扩展 |
| `IFileStorageProvider` | 存储提供者接口，返回当前存储实现；自定义实现可绕过 `DefaultStorageFactory` 的类型分发 | 框架内置 `DefaultStorageFactory`，业务可整体替换 |
| `VirusScanner` | 病毒扫描接口，实现后自动替换 `NoOpVirusScanner` | 框架内置 NoOp，业务方对接 ClamAV/ICAP |
| `CheckpointStore` | 检查点存储接口，可扩展为数据库 / Etcd 等存储 | 框架内置 Redis + Local + Delegating |
| `MultipartContextStore` | 分片上传上下文存储接口，可扩展为数据库 / Etcd 等 | 框架内置 Redis + InMemory + Delegating |
| `CheckpointService` | 检查点业务服务接口，封装序列化与校验逻辑 | 框架内置 `DefaultCheckpointService` |
| `UploadProgressListener` | 上传进度回调接口 | 业务方实现，作为方法参数传入 |
| `DefaultStorageFactory.register(type, provider)` | 通过代码注册自定义存储类型 | 业务方扩展（非 Bean 方式） |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/file` | 存储后端健康检查，由 `FileHealthIndicator` 注册 | `spring-boot-actuator` 在 classpath 且 `ydsz.file.enabled=true` |

`FileHealthIndicator` 暴露的详情字段：

| 字段 | 说明 |
|---|---|
| `storageType` | 当前存储实现类简单名（如 `MinioStorage`） |
| `storageTypeConfig` | 配置的存储类型（如 `minio`） |
| `bucketConfigured` | 是否配置了 bucket |
| `bucketExists` | bucket 是否实际存在（未配置 bucket 时跳过检查） |
| `dedupEnabled` | 是否启用了秒传服务 |
| `virusScanner` | 病毒扫描器实现类简单名（无则为 `none`） |
| `virusScannerAvailable` | 病毒扫描器是否可用 |
| `retryEnabled` | 是否启用重试助手 |
| `retryMaxRetries` | 重试次数（启用时） |
| `metricsEnabled` | 是否启用指标采集 |
| `magicNumberCheck` | 是否启用 Magic Number 校验 |
| `maxFileSize` | 单文件最大大小限制 |

健康检查判定逻辑：
- bucket 已配置但不存在 → **DOWN**
- 存储调用抛异常 → **DOWN**（详情中带 error 字段）
- 其他情况 → **UP**

## 注意事项

1. **生产环境必须替换 NoOpVirusScanner**：默认装配的 `NoOpVirusScanner` 仅返回 CLEAN 占位，不真正扫描病毒。生产环境必须实现 `VirusScanner` 接口注册为 Spring Bean，否则存在安全风险。
11. **启用秒传必须配套 Bucket Lifecycle 策略**：`FileDedupService` 基于 SHA-256 的文件内容去重（30 天 TTL）实现秒传。秒传命中后返回的 URL 可能指向已物理删除的对象（如果存储端已配置了 Bucket Lifecycle 自动清理）。生产环境在启用秒传的同时必须在存储桶上配置 Lifecycle 策略：例如"30 天内未访问的对象自动转冷 / 删除"，避免"幽灵秒传"。
12. **并发守卫 REJECT 策略需前端配合**：`UploadConcurrencyGuard` 在同一 objectKey 被并发上传时采用快速失败策略（抛 BusinessException，错误码 `FILE_CONCURRENT_UPLOAD`）。前端需要在 Controller 层捕获该异常并展示"文件正在上传中"的友好提示，避免直接暴露技术异常。
3. **Redis 不是必须依赖**：`MultipartContextStore` 与 `CheckpointStore` 优先使用 Redis，Redis 不可用时自动降级到内存 / 本地文件实现。多实例部署时必须引入 Redis，否则分片上传上下文无法跨实例共享。
4. **分片大小默认 5MB**：与 S3 协议对齐，过小会增加请求数，过大降低并发度与失败恢复效率。可通过 `ydsz.file.part-size` 调整。
5. **路径穿越防护**：`AbstractFileStorage.resolveObjectKey` 会拒绝空字节、`..` 路径穿越符（含 URL 编码 `%2e%2e`），并通过 `Paths.normalize()` 二次校验，业务层无需重复实现。
6. **批量删除不保证原子性**：`batchDelete` 基于 `parallelStream` 并行执行，部分失败时已成功的对象不可恢复，业务方需根据 `BatchDeleteResult.getFailedMap()` 进行业务补偿。
7. **并发保护策略**：`UploadConcurrencyGuard` 仅在 Redis 可用且 `ydsz.file.concurrency-control.enabled=true` 时生效；冲突时直接抛异常（REJECT 策略）。
8. **生命周期清理 dry-run**：生产环境首次启用建议 `dry-run=true` 试运行，确认清理范围后再切换为 `false`。
9. **`generateUploadPolicy` 与 `generatePresignedUploadUrl` 不是所有存储后端都支持**：默认抛 `UnsupportedOperationException`，各云存储实现类按需覆盖。
10. **分片 MD5 校验默认关闭**：启用 `ydsz.file.upload.chunk-md5-check=true` 会增加内存与 CPU 开销，建议仅在高一致性场景启用。启用后流式累积 MD5 仅缓存 `MessageDigest` 状态（约 128 字节），不缓存原始分片数据，避免 OOM。
11. **`FileConfiguration` 启用 `@EnableScheduling`**：引入本模块后会自动开启 Spring 调度，每小时清理过期分片上下文。若业务模块已有 `@EnableScheduling`，Spring 会自动去重，无副作用。

## 指标监控与 Grafana 看板

`FileMetrics` 基于 Micrometer 自动采集以下 `file.*` 前缀的指标（MeterRegistry 为 null 时安全降级为 No-Op）：

| 指标名 | 类型 | Tag | 说明 |
|--------|------|-----|------|
| `file.upload.count` | Counter | `storageType` | 上传总次数 |
| `file.download.count` | Counter | `storageType` | 下载总次数 |
| `file.delete.count` | Counter | `storageType` | 删除总次数 |
| `file.dedup.hit` | Counter | - | 秒传命中次数 |
| `file.dedup.miss` | Counter | - | 秒传未命中次数 |
| `file.virus.detected` | Counter | `scanner` | 病毒检出次数 |
| `file.upload.duration` | Timer | `storageType` | 上传耗时（P50/P95/P99） |
| `file.download.duration` | Timer | `storageType` | 下载耗时 |
| `file.upload.errors` | Counter | `code` | 按错误码分组的上传错误 |
| `file.download.errors` | Counter | `code` | 按错误码分组的下载错误 |

**接入 Prometheus + Grafana**：确认 `micrometer-registry-prometheus` 在 classpath 后，指标会自动暴露给 `/actuator/prometheus` 端点。建议创建专用 Dashboard 包含以下面板：

1. 上传 / 下载 QPS 聚合图（`rate(file.upload.count[5m])`）
2. 秒传命中率（`rate(file.dedup.hit[5m]) / (rate(file.dedup.hit[5m]) + rate(file.dedup.miss[5m]))`）
3. 上传延迟 P95 / P99（`histogram_quantile(0.95, rate(file.upload.duration_bucket[5m]))`）
4. 上传错误码分布（`sum by(code) (rate(file.upload.errors[5m]))`）
5. 病毒检出计数器（`file.virus.detected`，仅在替换真实 VirusScanner 后有效）

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
