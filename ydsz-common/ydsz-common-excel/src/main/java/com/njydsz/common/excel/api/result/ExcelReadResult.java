package com.njydsz.common.excel.api.result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Excel读取结果封装类 - 结果模式实现
 *
 * <p>封装Excel异步读取的结果,提供同步和异步两种获取方式。 支持链式调用、结果聚合、错误处理等高级功能。
 *
 * <h3>特点</h3>
 *
 * <ul>
 *   <li>结果封装 - 包含数据列表、总行数、读取耗时等信息
 *   <li>链式调用 - 支持流畅的API设计
 *   <li>异步支持 - 基于CompletableFuture实现
 *   <li>错误处理 - 封装异常信息,避免异常泄漏
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 方式1: 直接获取结果(同步等待)
 * ExcelReadResult<User> result = ExcelFacade.asyncRead("data.xlsx", User.class).submit();
 * List<User> users = result.getData();
 * log.info("读取了 {} 行数据", result.getTotalRows());
 *
 * // 方式2: 带超时等待
 * try {
 *     List<User> users = result.get(30, TimeUnit.SECONDS);
 * } catch (TimeoutException e) {
 *     log.warn("读取超时", e);
 * }
 *
 * // 方式3: 链式处理
 * result.thenAccept(users -> {
 *     log.info("处理了 {} 条数据", users.size());
 * }).exceptionally(e -> {
 *     log.error("读取失败", e);
 *     return null;
 * });
 *
 * // 方式4: 获取统计信息
 * log.info("总行数: {}", result.getTotalRows());
 * log.info("读取耗时: {}ms", result.getElapsedTime());
 * log.info("读取速度: {} 行/秒", result.getRowsPerSecond());
 * }</pre>
 *
 * @param <T> 泛型参数,表示读取的数据类型
 * @see ExcelFacade#asyncRead
 * @author ydsz-team

 * @version 26.09.01
 * @since 26.09.01
 */
public class ExcelReadResult<T> {

  /** 读取的数据列表 */
  private List<T> data;

  /** 总行数 */
  private int totalRows;

  /** 读取耗时(毫秒) */
  private long elapsedTime;

  /** 是否成功 */
  private boolean success;

  /** 异常信息 */
  private Throwable error;

  /** 原始CompletableFuture */
  private CompletableFuture<List<T>> future;

  /** 来源文件名（含扩展名），仅用于日志与问题定位，不参与业务判断 */
  private String fileName;

  /** 实际读取的 Sheet 名称；按索引读取且未解析到名称时可能为 null */
  private String sheetName;

  /** 实际读取的 Sheet 索引，从 0 开始 */
  private int sheetIndex;

  public ExcelReadResult() {}

  private ExcelReadResult(Builder<T> builder) {
    this.data = builder.data;
    this.totalRows = builder.totalRows;
    this.elapsedTime = builder.elapsedTime;
    this.success = builder.success;
    this.error = builder.error;
    this.future = builder.future;
    this.fileName = builder.fileName;
    this.sheetName = builder.sheetName;
    this.sheetIndex = builder.sheetIndex;
  }

  // ==================== 静态工厂方法 ====================

  /**
   * 创建成功结果
   *
   * @param data 读取的数据
   * @param elapsedTime 耗时
   * @param <T> 数据类型
   * @return 结果对象
   */
  public static <T> ExcelReadResult<T> success(List<T> data, long elapsedTime) {
    ExcelReadResult<T> result = new ExcelReadResult<>();
    result.data = data;
    result.totalRows = data != null ? data.size() : 0;
    result.elapsedTime = elapsedTime;
    result.success = true;
    return result;
  }

  /**
   * 创建失败结果
   *
   * @param error 异常信息
   * @param <T> 数据类型
   * @return 结果对象
   */
  public static <T> ExcelReadResult<T> failure(Throwable error) {
    ExcelReadResult<T> result = new ExcelReadResult<>();
    result.data = new ArrayList<>(16);