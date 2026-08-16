package com.njydsz.common.file.config;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.file.health.FileHealthIndicator;
import com.njydsz.common.file.lifecycle.FileLifecycleManager;
import com.njydsz.common.file.metrics.FileMetrics;
import com.njydsz.common.file.service.FileDedupService;
import com.njydsz.common.file.storage.CheckpointService;
import com.njydsz.common.file.storage.CheckpointStore;
import com.njydsz.common.file.storage.DefaultCheckpointService;
import com.njydsz.common.file.storage.DefaultStorageFactory;
import com.njydsz.common.file.storage.DelegatingCheckpointStore;
import com.njydsz.common.file.storage.DelegatingMultipartContextStore;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.file.storage.InMemoryMultipartContextStore;
import com.njydsz.common.file.storage.LocalCheckpointStore;
import com.njydsz.common.file.storage.MultipartContextStore;
import com.njydsz.common.file.storage.RedisCheckpointStore;
import com.njydsz.common.file.storage.RedisMultipartContextStore;
import com.njydsz.common.file.storage.StorageRetryHelper;
import com.njydsz.common.file.storage.UploadConcurrencyGuard;
import com.njydsz.common.file.util.FileTypeValidator;
import com.njydsz.common.file.virus.NoOpVirusScanner;
import com.njydsz.common.file.virus.VirusScanner;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 文件存储自动配置类
 *
 * <p>Spring Boot 自动装配入口，注册文件存储模块所需的全部 Bean。 通过 {@code ydsz.file.enabled=true} 控制是否启用（默认启用）。
 *
 * <p><b>注册的核心 Bean：</b>
 *
 * <ul>
 *   <li>{@link IFileStorageProvider} - 存储提供者（工厂模式创建具体存储实例）
 *   <li>{@link MultipartContextStore} - 分片上传上下文存储（优先 Redis，降级内存）
 *   <li>{@link CheckpointStore} - 断点续传检查点存储（优先 Redis，降级本地文件）
 *   <li>{@link CheckpointService} - 检查点业务服务（封装校验/恢复/MD5 累积计算）
 *   <li>{@link FileMetrics} - Micrometer 监控指标收集器
 *   <li>{@link VirusScanner} - 病毒扫描接口（默认空操作实现）
 *   <li>{@link StorageRetryHelper} - 存储操作重试助手
 *   <li>{@link FileDedupService} - 文件去重服务（秒传）
 *   <li>{@link FileLifecycleManager} - 文件生命周期管理器（过期清理）
 *   <li>{@link FileHealthIndicator} - Spring Boot Actuator 健康检查指示器
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({
  FileProperties.class,
  FileUploadProperties.class,
  FileLifecycleProperties.class
})
@ConditionalOnProperty(
    prefix = "ydsz.file",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class FileConfiguration {

  /** 分片上传上下文存储 */
  private final MultipartContextStore multipartContextStore;

  /** 分布式锁提供者（可选，ydsz-common-lock 在 classpath 时可用） */
  private final ObjectProvider<DistributedLocker> lockerProvider;

  /**
   * 注册分片上传上下文存储
   *
   * <p>优先使用 Redis，Redis 不可用时降级到内存 Map
   *
   * @param redisProvider Redis 模板提供者
   * @return 分片上传上下文存储实例
   */
  @Bean
  @ConditionalOnMissingBean(MultipartContextStore.class)
  public MultipartContextStore multipartContextStore(
      ObjectProvider<StringRedisTemplate> redisProvider) {
    StringRedisTemplate template = redisProvider.getIfAvailable();
    if (template != null) {
      return new DelegatingMultipartContextStore(new RedisMultipartContextStore(template), null);
    }
    log.warn("Redis not available, falling back to InMemoryMultipartContextStore.");
    return new InMemoryMultipartContextStore();
  }

  /**
   * 注册检查点存储
   *
   * <p>优先使用 Redis，Redis 不可用时降级到本地文件
   *
   * @param props 文件存储配置
   * @param redisProvider Redis 模板提供者
   * @return 检查点存储实例
   */
  @Bean
  @ConditionalOnMissingBean(CheckpointStore.class)
  public CheckpointStore checkpointStore(
      FileProperties props, ObjectProvider<StringRedisTemplate> redisProvider) {
    StringRedisTemplate template = redisProvider.getIfAvailable();
    CheckpointStore fallback = new LocalCheckpointStore(props.getCheckpointDir());
    if (template != null) {
      return new DelegatingCheckpointStore(new RedisCheckpointStore(template), fallback);
    }
    log.warn("Redis not available, falling back to LocalCheckpointStore.");
    return fallback;
  }

  /**
   * 注册检查点服务
   *
   * @param store 检查点存储
   * @param multipartStore 分片上传上下文存储
   * @return 检查点服务实例
   */
  @Bean
  @ConditionalOnMissingBean(CheckpointService.class)
  public CheckpointService checkpointService(
      CheckpointStore store, MultipartContextStore multipartStore) {
    return new DefaultCheckpointService(store, (b, o, u) -> Collections.emptyList(), 24 * 3600L);
  }

  /**
   * 注册文件监控指标收集器
   *
   * @param registryProvider Micrometer 指标注册中心提供者
   * @return 文件指标收集器实例
   */
  @Bean
  @ConditionalOnMissingBean(FileMetrics.class)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  public FileMetrics fileMetrics(ObjectProvider<MeterRegistry> registryProvider) {
    return new FileMetrics(registryProvider.getIfAvailable());
  }

  /**
   * 注册病毒扫描接口
   *
   * <p>当业务方未提供自定义实现时，注册空操作实现（所有文件视为 CLEAN）
   *
   * @return 病毒扫描实例
   */
  @Bean
  @ConditionalOnMissingBean(VirusScanner.class)
  public VirusScanner virusScanner() {
    log.info("No VirusScanner bean found, registering NoOpVirusScanner.");
    return new NoOpVirusScanner();
  }

  /**
   * 注册存储操作重试助手
   *
   * @param props 文件存储配置
   * @return 重试助手实例
   */
  @Bean
  @ConditionalOnMissingBean(StorageRetryHelper.class)
  public StorageRetryHelper storageRetryHelper(FileProperties props) {
    int retryCount = props.getRetryCount() != null ? props.getRetryCount() : 3;
    return new StorageRetryHelper(retryCount, 500L);
  }

  /**
   * 注册文件去重服务（秒传）
   *
   * @param redisStringOps Redis 字符串操作
   * @return 文件去重服务实例
   */
  @Bean
  @ConditionalOnBean({RedisStringOps.class})
  @ConditionalOnMissingBean(FileDedupService.class)
  public FileDedupService fileDedupService(RedisStringOps redisStringOps) {
    return new FileDedupService(redisStringOps);
  }

  /**
   * 注册文件生命周期管理器
   *
   * @param props 文件生命周期配置
   * @param provider 文件存储提供者
   * @return 文件生命周期管理器实例
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.file.lifecycle", name = "enabled", havingValue = "true")
  @ConditionalOnMissingBean(FileLifecycleManager.class)
  public FileLifecycleManager fileLifecycleManager(
      FileLifecycleProperties props, IFileStorageProvider provider) {
    return new FileLifecycleManager(props, provider);
  }

  /**
   * 注册文件存储提供者（工厂模式）
   *
   * <p>根据配置的存储类型创建对应的存储实例，并注入所有可选依赖
   *
   * @param fileProperties 文件存储配置
   * @param fileUploadProperties 分片上传配置
   * @param multipartContextStore 分片上传上下文存储
   * @param checkpointService 检查点服务
   * @param redisProvider Redis 模板提供者
   * @param dedupProvider 文件去重服务提供者
   * @param virusScannerProvider 病毒扫描接口提供者
   * @param metricsProvider 监控指标提供者
   * @param retryHelperProvider 重试助手提供者
   * @return 文件存储提供者实例
   */
  /**
   * 注册文件类型校验器 Bean。
   *
   * @param fileProperties 文件存储配置
   * @return 文件类型校验器实例
   */
  @Bean
  @ConditionalOnMissingBean(FileTypeValidator.class)
  public FileTypeValidator fileTypeValidator(FileProperties fileProperties) {
    return new FileTypeValidator(fileProperties.isCheckMagicNumber());
  }

  @Bean
  @ConditionalOnMissingBean(IFileStorageProvider.class)
  public IFileStorageProvider fileStorageProvider(
      FileProperties fileProperties,
      FileUploadProperties fileUploadProperties,
      MultipartContextStore multipartContextStore,
      CheckpointService checkpointService,
      ObjectProvider<StringRedisTemplate> redisProvider,
      ObjectProvider<FileDedupService> dedupProvider,
      ObjectProvider<VirusScanner> virusScannerProvider,
      ObjectProvider<FileMetrics> metricsProvider,
      ObjectProvider<StorageRetryHelper> retryHelperProvider,
      FileTypeValidator fileTypeValidator,
      ObjectProvider<ExecutorService> deleteExecutorProvider,
      ObjectProvider<ExecutorService> asyncUploadExecutorProvider) {
    DefaultStorageFactory factory = new DefaultStorageFactory(fileProperties, fileUploadProperties);
    factory.setMultipartContextStore(multipartContextStore);
    factory.setCheckpointService(checkpointService);
    factory.setFileTypeValidator(fileTypeValidator);
    UploadConcurrencyGuard guard =
        buildConcurrencyGuardIfEnabled(
            fileProperties, redisProvider.getIfAvailable(), lockerProvider);
    if (guard != null) factory.setConcurrencyGuard(guard);
    FileDedupService dedup = dedupProvider.getIfAvailable();
    if (dedup != null) factory.setFileDedupService(dedup);
    VirusScanner scanner = virusScannerProvider.getIfAvailable();
    if (scanner != null) factory.setVirusScanner(scanner);
    FileMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) factory.setFileMetrics(metrics);
    StorageRetryHelper retryHelper = retryHelperProvider.getIfAvailable();
    if (retryHelper != null) factory.setRetryHelper(retryHelper);
    // 注入 ydsz-common-thread 管理的线程池（Bean 名称：fileDeleteExecutor / fileUploadExecutor）
    ExecutorService deleteExecutor = deleteExecutorProvider.getIfAvailable();
    if (deleteExecutor != null) factory.setDeleteExecutor(deleteExecutor);
    ExecutorService asyncUploadExecutor = asyncUploadExecutorProvider.getIfAvailable();
    if (asyncUploadExecutor != null) factory.setAsyncUploadExecutor(asyncUploadExecutor);
    return factory;
  }

  /**
   * 构建上传并发保护器（仅在 Redis 可用且配置启用时创建）
   *
   * <p>当 {@link DistributedLocker} 可用时（ydsz-common-lock 在 classpath 上）， 使用其 {@code tryLock}/{@code
   * unlock} 实现加锁 / 释放，享有 Lua 原子释放与 WatchDog 续期能力； 否则降级为原生 {@link StringRedisTemplate} 操作。
   *
   * @param props 文件存储配置
   * @param redis Redis 模板实例
   * @param lockerProvider 分布式锁提供者（可选）
   * @return 并发保护器实例，不需要时返回 null
   */
  private UploadConcurrencyGuard buildConcurrencyGuardIfEnabled(
      FileProperties props,
      StringRedisTemplate redis,
      ObjectProvider<DistributedLocker> lockerProvider) {
    if (redis == null) return null;
    var config = props.getConcurrencyControl();
    if (config == null || !config.isEnabled()) return null;
    DistributedLocker locker = lockerProvider.getIfAvailable();
    if (locker == null) {
      log.info("[FileConfiguration] DistributedLocker 不可用，UploadConcurrencyGuard 降级为原生 Redis 操作");
    }
    return new UploadConcurrencyGuard(locker, redis);
  }

  /**
   * 注册文件存储健康检查指示器
   *
   * @param provider 文件存储提供者
   * @param props 文件存储配置
   * @param dedupProvider 文件去重服务提供者
   * @param virusScannerProvider 病毒扫描接口提供者
   * @param retryHelperProvider 重试助手提供者
   * @param metricsProvider 监控指标提供者
   * @return 健康检查指示器实例
   */
  @Bean
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  @ConditionalOnMissingBean(FileHealthIndicator.class)
  public FileHealthIndicator storageHealthIndicator(
      IFileStorageProvider provider,
      FileProperties props,
      ObjectProvider<FileDedupService> dedupProvider,
      ObjectProvider<VirusScanner> virusScannerProvider,
      ObjectProvider<StorageRetryHelper> retryHelperProvider,
      ObjectProvider<FileMetrics> metricsProvider) {
    return new FileHealthIndicator(
        provider, props, dedupProvider, virusScannerProvider, retryHelperProvider, metricsProvider);
  }
}
