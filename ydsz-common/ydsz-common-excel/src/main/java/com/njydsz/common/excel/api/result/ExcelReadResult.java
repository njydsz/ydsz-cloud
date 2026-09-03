package com.njydsz.common.excel.api.result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

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
    result.totalRows = 0;
    result.error = error;
    result.success = false;
    return result;
  }

  // ==================== Getter 方法 ====================

  public List<T> getData() {
    return data;
  }

  public int getTotalRows() {
    return totalRows;
  }

  public long getElapsedTime() {
    return elapsedTime;
  }

  public boolean isSuccess() {
    return success;
  }

  public Throwable getError() {
    return error;
  }

  public String getFileName() {
    return fileName;
  }

  public String getSheetName() {
    return sheetName;
  }

  public int getSheetIndex() {
    return sheetIndex;
  }

  // ==================== 异步获取方法 ====================

  /**
   * 同步获取数据,如果结果来自异步任务则阻塞等待
   *
   * @return 数据列表
   */
  public List<T> get() {
    if (future != null) {
      try {
        return future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("获取Excel读取结果时被中断", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Excel读取执行异常", e.getCause());
      }
    }
    return data;
  }

  /**
   * 带超时的同步获取数据
   *
   * @param timeout 超时时间
   * @param unit 时间单位
   * @return 数据列表
   * @throws TimeoutException 超时异常
   */
  public List<T> get(long timeout, TimeUnit unit) throws TimeoutException {
    if (future != null) {
      try {
        return future.get(timeout, unit);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("获取Excel读取结果时被中断", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Excel读取执行异常", e.getCause());
      }
    }
    return data;
  }

  /**
   * 获取异步任务的CompletableFuture
   *
   * @return CompletableFuture
   */
  public CompletableFuture<List<T>> getFuture() {
    return future;
  }

  /**
   * 异步处理结果
   *
   * @param action 处理动作
   * @return CompletableFuture
   */
  public CompletableFuture<Void> thenAccept(Consumer<List<T>> action) {
    if (future != null) {
      return future.thenAccept(action);
    }
    action.accept(data);
    return CompletableFuture.completedFuture(null);
  }

  /**
   * 异步处理异常
   *
   * @param action 异常处理动作
   * @return CompletableFuture
   */
  public CompletableFuture<List<T>> exceptionally(Function<Throwable, List<T>> action) {
    if (future != null) {
      return future.exceptionally(action);
    }
    if (error != null) {
      data = action.apply(error);
    }
    return CompletableFuture.completedFuture(data);
  }

  /**
   * 判断是否有错误
   *
   * @return 如果有错误返回true
   */
  public boolean hasError() {
    return error != null;
  }

  /**
   * 计算读取速度(行/秒)
   *
   * @return 读取速度,如果耗时为0则返回0
   */
  public double getRowsPerSecond() {
    if (elapsedTime <= 0) {
      return 0;
    }
    return (double) totalRows / elapsedTime * 1000;
  }

  // ==================== Builder ====================

  /**
   * 创建Builder实例
   *
   * @param <T> 数据类型
   * @return Builder
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Builder类用于构建ExcelReadResult
   *
   * @param <T> 数据类型
   */
  public static class Builder<T> {
    private List<T> data;
    private int totalRows;
    private long elapsedTime;
    private boolean success;
    private Throwable error;
    private CompletableFuture<List<T>> future;
    private String fileName;
    private String sheetName;
    private int sheetIndex;

    public Builder<T> data(List<T> data) {
      this.data = data;
      return this;
    }

    public Builder<T> totalRows(int totalRows) {
      this.totalRows = totalRows;
      return this;
    }

    public Builder<T> elapsedTime(long elapsedTime) {
      this.elapsedTime = elapsedTime;
      return this;
    }

    public Builder<T> success(boolean success) {
      this.success = success;
      return this;
    }

    public Builder<T> error(Throwable error) {
      this.error = error;
      return this;
    }

    public Builder<T> future(CompletableFuture<List<T>> future) {
      this.future = future;
      return this;
    }

    public Builder<T> fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder<T> sheetName(String sheetName) {
      this.sheetName = sheetName;
      return this;
    }

    public Builder<T> sheetIndex(int sheetIndex) {
      this.sheetIndex = sheetIndex;
      return this;
    }

    /**
     * 构建ExcelReadResult实例
     *
     * @return ExcelReadResult
     */
    public ExcelReadResult<T> build() {
      return new ExcelReadResult<>(this);
    }
  }
}

