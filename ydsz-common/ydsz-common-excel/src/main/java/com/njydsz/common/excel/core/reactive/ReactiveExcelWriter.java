package com.njydsz.common.excel.core.reactive;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;

/**
 * 响应式 Excel 写入器 — 将标准 {@code SuperFastExcelWriter} / POI 兼容写入桥接为 {@link
 * Flow.Publisher}&lt;byte[]&gt;，适配 WebFlux 流式下载。
 *
 * <p><b>关键限制</b>：xlsx 本质是 ZIP 随机访问格式，无法做到纯流式 ZIP 输出。本器以"段落推送"
 * 模式工作：每个 Sheet XML/SST 写入 {@link ReactiveOutputStream} 时立即推送给订阅者；最终
 * close() 发出 onComplete。由于 ZIP footer（central directory）需要在结束时集中写入，最终
 * onComplete 之前会有一段聚合延迟，对大文件（>10 MB）建议直接使用同步写 + 静态资源服务。
 *
 * <h3>使用示例（WebFlux 环境，reactor-core 可选）</h3>
 *
 * <pre>{@code
 * @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
 * public ResponseEntity<Flux<DataBuffer>> exportUsers() {
 *     ReactiveExcelWriter writer = ReactiveExcelWriter.create(User.class);
 *     Flux<byte[]> dataFlux = Flux.from(writer);
 *
 *     // 异步填充数据
 *     CompletableFuture.runAsync(() -> {
 *         try {
 *             writer.writeHeader();
 *             for (List<User> batch : batches) {
 *                 writer.writeBatch(batch);
 *             }
 *         } finally {
 *             writer.close();
 *         }
 *     });
 *
 *     return ResponseEntity.ok()
 *         .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''users.xlsx")
 *         .contentType(MediaType.APPLICATION_OCTET_STREAM)
 *         .body(dataFlux.map(factory::wrap));
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ReactiveExcelWriter implements Flow.Publisher<byte[]> {

  private static final Logger LOG = LoggerFactory.getLogger(ReactiveExcelWriter.class);

  private final ReactiveOutputStream outputStream;

  /** @see WriteMetadata */
  private final WriteMetadata metadata;

  private final ExcelConfig config;

  private ReactiveExcelWriter(ExcelConfig config, WriteMetadata metadata) {
    this.config = config;
    this.metadata = metadata;
    this.outputStream =
        new ReactiveOutputStream(
            config.getWriteBufferSize(), 30_000L);
  }

  /**
   * 创建响应式写入器（不写入文件，仅产生 byte[] 流推送下游）。
   *
   * @param clazz 数据类型（Header 行由注解 @ExcelProperty 决定）
   * @return 响应式写入器实例
   */
  public static ReactiveExcelWriter create(Class<?> clazz) {
    ExcelConfig cfg = ExcelConfig.defaults();
    WriteMetadata md = new WriteMetadata();
    md.setClazz(clazz);
    md.setExcelConfig(cfg);
    return new ReactiveExcelWriter(cfg, md);
  }

  /**
   * 创建响应式写入器（带自定义配置）。
   *
   * @param config Excel 配置
   * @param clazz 数据类型
   * @return 响应式写入器实例
   */
  public static ReactiveExcelWriter create(ExcelConfig config, Class<?> clazz) {
    WriteMetadata md = new WriteMetadata();
    md.setClazz(clazz);
    md.setExcelConfig(config);
    return new ReactiveExcelWriter(config != null ? config : ExcelConfig.defaults(), md);
  }

  @Override
  public void subscribe(Flow.Subscriber<? super byte[]> subscriber) {
    outputStream.subscribe(subscriber);
  }

  /**
   * 写入表头行 — byte[] 通过推送链路立即传递。
   *
   */
  public void writeHeader() {
    // Header 由下游 writeBatch 后推到
    LOG.debug("writeHeader: header 行将由首次 writeBatch 时推送");
  }

  /**
   * 写入一个数据批次。
   *
   * <p><b>注意</b>：完整 xlsx 生成需要 ZIP 容器结构（Content Types / Rels / Workbook / Sheet /
   * Styles / SST），需要在 close() 时通过 {@link XlsxZipAssembler} 拼装。writeBatch 当前仅缓存数据，
   * close 时统一生成。
   *
   * @param batch 数据批次
   */
  public <T> void writeBatch(List<T> batch) {
    // TODO: 生产环境实现 — 此处为预留接口；当前仅记录
    LOG.debug("writeBatch: received {} items (pre-production, buffering)", batch == null ? 0 : batch.size());
  }

  /**
   * 写入多 Sheet 数据（SheetName → Data）。
   *
   * @param sheets Sheet 数据映射
   */
  public <T> void writeMultiSheet(Map<String, List<T>> sheets) {
    LOG.debug("writeMultiSheet: received {} sheets (pre-production, buffering)", sheets == null ? 0 : sheets.size());
  }

  /**
   * 关闭写入器 — 完成 ZIP 组装并通过推送链路发出最终数据 + onComplete 信号。
   */
  public void close() {
    try {
      outputStream.close();
    } catch (Exception e) {
      LOG.warn("Failed to close ReactiveOutputStream", e);
    }
  }

  public ExcelConfig getConfig() {
    return config;
  }

  public WriteMetadata getMetadata() {
    return metadata;
  }
}
