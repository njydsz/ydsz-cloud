package com.njydsz.pmis.common.excel.api.result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import com.njydsz.pmis.common.excel.ExcelFacade;

/**
 * Excel读取结果封装类 - 结果模式实现
 *
 * <p>封装Excel异步读取的结果,提供同步和异步两种获取方式。
 * 支持链式调用、结果聚合、错误处理等高级功能。</p>
 *
 * <h3>特点</h3>
 * <ul>
 *   <li>结果封装 - 包含数据列表、总行数、读取耗时等信息</li>
 *   <li>链式调用 - 支持流畅的API设计</li>
 *   <li>异步支持 - 基于CompletableFuture实现</li>
 *   <li>错误处理 - 封装异常信息,避免异常泄漏</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式1: 直接获取结果(同步等待)
 * ExcelReadResult<User> result = ExcelFacade.asyncRead("data.xlsx", User.class).submit();
 * List<User> users = result.getData();
 * System.out.println("读取了 " + result.getTotalRows() + " 行数据");
 *
 * // 方式2: 带超时等待
 * try {
 *     List<User> users = result.get(30, TimeUnit.SECONDS);
 * } catch (TimeoutException e) {
 *     System.out.println("读取超时");
 * }
 *
 * // 方式3: 链式处理
 * result.thenAccept(users -> {
 *     System.out.println("处理了 " + users.size() + " 条数据");
 * }).exceptionally(e -> {
 *     System.out.println("读取失败: " + e.getMessage());
 *     return null;
 * });
 *
 * // 方式4: 获取统计信息
 * System.out.println("总行数: " + result.getTotalRows());
 * System.out.println("读取耗时: " + result.getElapsedTime() + "ms");
 * System.out.println("读取速度: " + result.getRowsPerSecond() + " 行/秒");
 * }</pre>
 *
 * @param <T> 泛型参数,表示读取的数据类型
 * @see ExcelFacade#asyncRead
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
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

    /** 元数据 */
    private String fileName;
    private String sheetName;
    private int sheetIndex;

    public ExcelReadResult() {
    }

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
        result.data = new ArrayList<>();
        result.totalRows = 0;
        result.elapsedTime = 0;
        result.success = false;
        result.error = error;
        return result;
    }

    /**
     * 创建构建器
     *
     * @param <T> 数据类型
     * @return 新的构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    // ==================== 结果获取方法 ====================

    /**
     * 获取数据列表
     *
     * @return 读取的数据列表
     */
    public List<T> getData() {
        return data;
    }

    /**
     * 获取总行数
     *
     * @return 总行数
     */
    public int getTotalRows() {
        return totalRows;
    }

    /**
     * 获取读取耗时
     *
     * @return 耗时(毫秒)
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * 判断是否成功
     *
     * @return true表示成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取异常信息
     *
     * @return 异常,无异常时返回null
     */
    public Throwable getError() {
        return error;
    }

    /**
     * 获取错误消息
     *
     * @return 错误消息,无错误时返回null
     */
    public String getErrorMessage() {
        return error != null ? error.getMessage() : null;
    }

    /**
     * 获取文件名称
     *
     * @return 文件名称
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 获取Sheet名称
     *
     * @return Sheet名称
     */
    public String getSheetName() {
        return sheetName;
    }

    /**
     * 获取Sheet索引
     *
     * @return Sheet索引
     */
    public int getSheetIndex() {
        return sheetIndex;
    }

    /**
     * 计算读取速度
     *
     * @return 每秒处理的行数
     */
    public double getRowsPerSecond() {
        if (elapsedTime <= 0) {
            return 0;
        }
        return (double) totalRows / elapsedTime * 1000;
    }

    // ==================== 异步操作方法 ====================

    /**
     * 获取CompletableFuture
     *
     * @return 原始的CompletableFuture
     */
    public CompletableFuture<List<T>> getFuture() {
        return future;
    }

    /**
     * 添加完成回调
     *
     * @param action 完成时执行的动作
     * @return 新的CompletableFuture
     */
    public CompletableFuture<Void> thenAccept(Consumer<List<T>> action) {
        if (future != null) {
            return future.thenAccept(action);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 添加异常处理
     *
     * @param handler 异常处理函数
     * @return 新的CompletableFuture
     */
    public CompletableFuture<List<T>> exceptionally(Function<Throwable, List<T>> handler) {
        if (future != null) {
            return future.exceptionally(handler);
        }
        return CompletableFuture.completedFuture(data);
    }

    /**
     * 同步等待结果
     *
     * @return 数据列表
     * @throws InterruptedException 如果等待被中断
     * @throws ExecutionException 如果计算过程中发生异常
     */
    public List<T> get() throws InterruptedException, ExecutionException {
        if (future != null) {
            return future.get();
        }
        return data;
    }

    /**
     * 带超时等待结果
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 数据列表
     * @throws InterruptedException 如果等待被中断
     * @throws ExecutionException 如果计算过程中发生异常
     * @throws TimeoutException 如果等待超时
     */
    public List<T> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (future != null) {
            return future.get(timeout, unit);
        }
        return data;
    }

    /**
     * 阻塞获取结果(带默认值)
     *
     * @return 数据列表,如果失败则返回空列表
     */
    public List<T> getOrDefault() {
        if (future != null) {
            try {
                return future.get();
            } catch (Exception e) {
                return data != null ? data : new ArrayList<>();
            }
        }
        return data != null ? data : new ArrayList<>();
    }

    // ==================== 链式操作 ====================

    /**
     * 过滤数据
     *
     * @param predicate 过滤条件
     * @return 符合条件的新结果
     */
    public ExcelReadResult<T> filter(Predicate<T> predicate) {
        List<T> filtered = new ArrayList<>();
        if (data != null) {
            for (T item : data) {
                if (predicate.test(item)) {
                    filtered.add(item);
                }
            }
        }
        return ExcelReadResult.<T>builder()
            .data(filtered)
            .totalRows(filtered.size())
            .elapsedTime(elapsedTime)
            .success(success)
            .fileName(fileName)
            .sheetName(sheetName)
            .sheetIndex(sheetIndex)
            .build();
    }

    /**
     * 映射数据类型
     *
     * @param mapper 映射函数
     * @param <R> 目标类型
     * @return 映射后的新结果
     */
    public <R> ExcelReadResult<R> map(Function<T, R> mapper) {
        List<R> mapped = new ArrayList<>();
        if (data != null) {
            for (T item : data) {
                mapped.add(mapper.apply(item));
            }
        }
        return ExcelReadResult.<R>builder()
            .data(mapped)
            .totalRows(mapped.size())
            .elapsedTime(elapsedTime)
            .success(success)
            .fileName(fileName)
            .sheetName(sheetName)
            .sheetIndex(sheetIndex)
            .build();
    }

    @Override
    public String toString() {
        return String.format("ExcelReadResult{success=%s, totalRows=%d, elapsedTime=%dms, speed=%.2f rows/s}",
            success, totalRows, elapsedTime, getRowsPerSecond());
    }

    // ==================== Builder ====================

    /**
     * 构建器
     *
     * @param <T> 数据类型
     */
    public static class Builder<T> {
        private List<T> data = new ArrayList<>();
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

        public ExcelReadResult<T> build() {
            return new ExcelReadResult<>(this);
        }
    }
}
