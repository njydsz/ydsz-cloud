package com.njydsz.common.excel.core.async;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelReader;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;

/**
 * 异步读取桥接 — 利用 Java 21 虚拟线程实现不阻塞调用线程的 Excel 读取。
 *
 * <p>提供两种异步模式：
 *
 * <ul>
 *   <li>{@link #readAllAsync(InputStream, Class)} — 全量异步读取，返回 {@link CompletableFuture}</li>
 *   <li>{@link #streamAsync(InputStream, Class, BiConsumer)} — 异步流式处理，逐行回调</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 全量异步读取
 * CompletableFuture&lt;List&lt;User&gt;&gt; future = AsyncReadBridge.readAllAsync(is, User.class);
 * future.thenAccept(users -&gt; {
 *   log.info("异步读取完成，共 {} 条", users.size());
 * });
 *
 * // 异步流式读取（逐行回调在 IO 线程）
 * AsyncReadBridge.streamAsync(is, User.class, (ctx, user) -&gt; {
 *   processUser(user);
 * });
 * }</pre>
 *
 * <p>底层使用 Java {@code Thread.ofVirtual()} 启动虚拟线程执行物理 IO，
 * 主线程不参与 IO 阻塞；对于大文件（>100MB），建议改用流式模式以避免全量驻留内存。
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class AsyncReadBridge {

  private static final Logger LOG = LoggerFactory.getLogger(AsyncReadBridge.class);

  private AsyncReadBridge() {}

  /**
   * 全量异步读取 Excel 到内存列表（默认使用虚拟线程执行器）。
   *
   * @param is 输入流
   * @param clazz 映射类型
   * @param <T> 泛型
   * @return 异步读取结果
   */
  public static <T> CompletableFuture<List<T>> readAllAsync(InputStream is, Class<T> clazz) {
    Executor vThreads = virtualThreadExecutor();
    return readAllAsync(is, clazz, vThreads);
  }

  /**
   * 全量异步读取 Excel 到内存列表（使用自定义执行器）。
   *
   * @param is 输入流
   * @param clazz 映射类型
   * @param executor 自定义Executor（推荐使用newVirtualThreadPerTaskExecutor）
   * @param <T> 泛型
   * @return 异步读取结果
   */
  public static <T> CompletableFuture<List<T>> readAllAsync(InputStream is, Class<T> clazz,
      Executor executor) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        List<T> results = new ArrayList<>();
        ExcelReader reader = ExcelFacade.read(is, clazz);
        reader.doRead(new ReadListener<T>() {
          @Override
          public void onStart(AnalysisContext ctx) {
            // no-op
          }
          @Override
          public void onData(AnalysisContext ctx, T data) {
            results.add(data);
          }
          @Override
          public void onEnd(AnalysisContext ctx) {
            // no-op
          }
          @Override
          public void onError(AnalysisContext ctx, Exception e) {
            throw new RuntimeException(e);
          }
        });
        return results;
      } catch (Exception e) {
        LOG.error("Async read failed", e);
        throw new RuntimeException("Async read failed: " + e.getMessage(), e);
      }
    }, executor);
  }

  /**
   * 异步流式处理 — 在虚拟线程中解析数据，逐行回调到用户提供的消费者。
   *
   * <p>回调在 IO 线程（虚拟线程）中执行；用户如果需跨线程传递数据需自行保证线程安全。
   *
   * @param is 输入流
   * @param clazz 映射类型
   * @param consumer 行处理回调
   * @param <T> 泛型
   * @return 异步任务句柄（可等待完成）
   */
  public static <T> CompletableFuture<Void> streamAsync(InputStream is, Class<T> clazz,
      BiConsumer<AnalysisContext, T> consumer) {
    Executor vThreads = virtualThreadExecutor();
    return CompletableFuture.runAsync(() -> {
      try {
        ExcelReader reader = ExcelFacade.read(is, clazz);
        reader.doRead(new ReadListener<T>() {
          @Override
          public void onStart(AnalysisContext ctx) {
            // no-op
          }
          @Override
          public void onData(AnalysisContext ctx, T data) {
            try {
              consumer.accept(ctx, data);
            } catch (Exception e) {
              LOG.warn("Line consumer error: {}", e.getMessage(), e);
            }
          }
          @Override
          public void onEnd(AnalysisContext ctx) {
            // no-op
          }
          @Override
          public void onError(AnalysisContext ctx, Exception e) {
            LOG.warn("Line error: {}", e.getMessage(), e);
          }
        });
      } catch (Exception e) {
        throw new RuntimeException("Async stream failed", e);
      }
    }, vThreads);
  }

  /**
   * 并发读取多个 Excel 文件，全部完成后合并结果。
   *
   * <p>使用 CompletableFuture.allOf 实现并发控制；任一文件失败则整体失败。
   *
   * @param files 每个文件 { inputStream, class }
   * @param result 成功回调（主线程调用）
   * @param error 失败回调（主线程调用）
   */
  public static <T> void readFilesConcurrently(
      List<FileInput<T>> files,
      Consumer<List<T>> result,
      Consumer<Throwable> error) {
    if (files == null || files.isEmpty()) {
      result.accept(new ArrayList<>());
      return;
    }
    Executor vThreads = virtualThreadExecutor();
    @SuppressWarnings("unchecked")
    CompletableFuture<List<T>>[] futures = new CompletableFuture[files.size()];
    for (int i = 0; i < files.size(); i++) {
      final FileInput<T> fi = files.get(i);
        futures[i] = CompletableFuture.supplyAsync(() -> {
          List<T> list = new ArrayList<>();
          ExcelFacade.read(fi.is, fi.clazz).doRead(new ReadListener<T>() {
            @Override
            public void onStart(AnalysisContext ctx) {
              // no-op
            }
            @Override
            public void onData(AnalysisContext ctx, T data) {
              list.add(data);
            }
            @Override
            public void onEnd(AnalysisContext ctx) {
              // no-op
            }
            @Override
            public void onError(AnalysisContext ctx, Exception e) {
              throw new RuntimeException(e);
            }
          });
          return list;
        }, vThreads);
    }
    CompletableFuture.allOf(futures).whenComplete((unused, throwable) -> {
      if (throwable != null) {
        if (error != null) {
          error.accept(throwable);
        }
      } else {
        List<T> merged = new ArrayList<>();
        for (CompletableFuture<List<T>> f : futures) {
          merged.addAll(f.join());
        }
        result.accept(merged);
      }
    });
  }

  /** 文件输入封装 */
  public static final class FileInput<T> {
    public final InputStream is;
    public final Class<T> clazz;

    public FileInput(InputStream is, Class<T> clazz) {
      this.is = is;
      this.clazz = clazz;
    }

    public static <T> FileInput<T> of(InputStream is, Class<T> clazz) {
      return new FileInput<>(is, clazz);
    }
  }

  /**
   * 接收两个参数的消费者（避免引入第三方库）。
   */
  @FunctionalInterface
  public interface BiConsumer<T, U> {
    void accept(T t, U u);
  }

  /** 创建虚拟线程执行器（或传统线程池回退） */
  private static Executor virtualThreadExecutor() {
    try {
      // Java 21+：Thread.ofVirtual().factory()
      return (Executor) Executors.class
          .getMethod("newVirtualThreadPerTaskExecutor")
          .invoke(null);
    } catch (Exception e) {
      // fallback: 传统守护线程池
      return Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "excel-async-" + System.nanoTime());
        t.setDaemon(true);
        return t;
      });
    }
  }
}
