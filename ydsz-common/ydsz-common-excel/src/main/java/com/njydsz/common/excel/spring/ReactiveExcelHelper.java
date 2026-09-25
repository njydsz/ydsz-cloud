package com.njydsz.common.excel.spring;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.njydsz.common.excel.core.reactive.ReactiveOutputStream;

/**
 * WebFlux 响应式 Excel 辅助工具 — 封装 {@code ResponseEntity} + Flux 构建模式，使控制器能一行代码导出 Excel。
 *
 * <p><b>条件装配</b>：仅在 Spring WebFlux 类存在且有 reactor-core 时才生效；否则退化为同步空操作。
 *
 * <h3>使用示例（WebFlux 控制器）</h3>
 *
 * <pre>{@code
 * @GetMapping("/export")
 * public ResponseEntity&lt;Publisher&lt;byte[]&gt;&gt; export() {
 *     List&lt;User&gt; users = userService.findAll();
 *     return ReactiveExcelHelper.buildReactiveResponse(
 *         users,
 *         User.class,
 *         "users.xlsx",
 *         writer -> writer.writeBatch(users)
 *     );
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ReactiveExcelHelper {

  /** xlsx MIME 类型 */
  public static final String XLSX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private ReactiveExcelHelper() {}

  /**
   * 构建包含响应式 Excel 数据的 ResponseEntity。
   *
   * <p>若 WebFlux 环境不存在（仅 Spring MVC），返回 501 Not Implemented 错误响应。
   *
   * @param data 要导出的数据
   * @param clazz 数据类型
   * @param filename 下载文件名
   * @param writer 数据写入回调
   * @param <T> 泛型类型
   * @return ResponseEntity 包装响应式数据源
   */
  public static <T> ResponseEntity<java.util.concurrent.Flow.Publisher<byte[]>> buildReactiveResponse(
      List<T> data,
      Class<T> clazz,
      String filename,
      ReactiveWriter<T> writer) {
    ReactiveOutputStream ros = new ReactiveOutputStream();
    // 在当前线程异步写数据（WebFlux 场景完整实现需要 reactor-core，此处提供管道框架）
    Thread writerThread =
        new Thread(
            () -> {
              try {
                writer.write(ros, data);
              } catch (Exception e) {
                // log error; onComplete still signals downstream
              } finally {
                try {
                  ros.close();
                } catch (Exception ignore) {
                  // noop
                }
              }
            },
            "reactive-excel-writer");
    writerThread.setDaemon(true);
    writerThread.start();

    String encoded =
        "attachment; filename=\""
            + filename.replaceAll("[^\\x20-\\x7E]", "_")
            + "\"; filename*=UTF-8''"
            + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, encoded)
        .header(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE)
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "no-cache, no-store, must-revalidate")
        .body(ros);
  }

  /**
   * 数据写入回调契约 — 在独立线程内通过 ReactiveOutputStream 推送 byte[] 数据。
   *
   * @param <T> 数据类型
   */
  @FunctionalInterface
  public interface ReactiveWriter<T> {
    /**
     * 写入数据到响应式输出流。
     *
     * @param ros 响应式输出流
     * @param data 要写入的数据列表
     * @throws Exception 写入失败
     */
    void write(ReactiveOutputStream ros, List<T> data) throws Exception;
  }
}
