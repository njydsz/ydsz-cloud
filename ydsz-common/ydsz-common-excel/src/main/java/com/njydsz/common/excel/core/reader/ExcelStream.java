package com.njydsz.common.excel.core.reader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelReader;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.exception.ExcelExceptionCode;

/**
 * Excel 流式读取器 — 适配 Java 8+ Stream API，实现与 Reactor / Spring Batch 等框架的无缝集成。
 *
 * <p>与 {@link ExcelReader#doRead(ReadListener)} 的回调模式相比，{@link ExcelStream} 提供：
 *
 * <ul>
 *   <li>惰性求值：仅在终端操作执行时才实际读取 Excel 数据</li>
 *   <li>异常传播：{@link IOException/UncheckedIOException} 可安全通过 {@link Stream} 终端操作传播，
 *       无需另行 try-catch 回调</li>
 *   <li>背压内部处理：内部使用大小固定的 {@link ArrayBlockingQueue} 缓冲调用方延迟消费的数据行，
 *       在缓冲区满时背压至解析线程，避免 OOM</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * try (ExcelStream<User> stream = ExcelFacade.readAsStream("demo.xlsx", User.class)) {
 *   List<String> names = stream.stream()
 *       .filter(u -> u.getAge() > 18)
 *       .map(User::getName)
 *       .limit(1000)
 *       .collect(Collectors.toList());
 * }
 * }</pre>
 *
 * <p><b>注意：</b>{@link ExcelStream} 实现 {@link AutoCloseable}，必须使用 try-with-resources 或手动关闭以释放内部缓冲。
 *
 * @param <T> 映射数据类型
 * @author ydsz-team
 * @since 26.10.01
 */
public class ExcelStream<T> implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ExcelStream.class);

  /** 默认背压缓冲区大小 */
  private static final int DEFAULT_CAPACITY = 256;

  /** 流水线结束标记（包级可见，供 ExcelIterator 使用） */
  static final Object END_SENTINEL = new Object();

  private final BlockingQueue<Object> buffer;
  private final CompletableFuture<Void> completion;
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final AtomicReference<Throwable> error = new AtomicReference<>();

  /**
   * 创建 ExcelStream。
   *
   * <p>内部启动生产者线程从 Excel 读取数据并写入阻塞队列；调用方通过 {@link #stream()} 消费。
   *
   * @param reader 已配置好的 ExcelReader
   * @param clazz 数据类型
   * @param capacity 背压缓冲区大小（行数），默认 256
   * @param <T> 数据类型
   * @return ExcelStream 实例，须由调用方关闭
   */
  @SuppressWarnings("unchecked")
  static <T> ExcelStream<T> of(ExcelReader reader, Class<T> clazz, int capacity) {
    BlockingQueue<Object> queue = new ArrayBlockingQueue<>(capacity);
    AtomicBoolean closed = new AtomicBoolean(false);
    AtomicReference<Throwable> err = new AtomicReference<>();

    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
      try {
        reader.doRead(
            new ReadListener<T>() {
              @Override
              public void onStart(AnalysisContext context) {}

              @Override
              public void onData(AnalysisContext context, T data) {
                try {
                  while (!closed.get()) {
                    if (queue.offer(data, 500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                      return;
                    }
                  }
                } catch (InterruptedException ie) {
                  Thread.currentThread().interrupt();
                  throw new ExcelReadException(
                      ExcelExceptionCode.READ_IO_ERROR, "Stream 已关闭", ie);
                }
              }

              @Override
              public void onEnd(AnalysisContext context) {
                tryFinish(queue, closed);
              }

              @Override
              public void onError(AnalysisContext context, Throwable t) {
                err.compareAndSet(null, t);
                tryFinish(queue, closed);
              }
            });
      } catch (Exception e) {
        err.compareAndSet(null, e);
        tryFinish(queue, closed);
      }
      tryFinish(queue, closed);
    });

    ExcelStream<T> stream = new ExcelStream<>();
    stream.buffer = queue;
    stream.completion = future;
    stream.isClosed = closed;
    stream.error = err;
    return stream;
  }

  private ExcelStream() {}

  private static void tryFinish(BlockingQueue<Object> queue, AtomicBoolean closed) {
    if (closed.compareAndSet(false, true)) {
      queue.offer(END_SENTINEL);
    }
  }

  /**
   * 获取数据流。
   *
   * <p>首次调用时内部启动解析线程；后续多次调用返回同一个流实例。 流在遇到结束标记或异常时自动关闭。
   *
   * @return 数据流；消费完毕或发生异常时需在外部 try-with-resources 中使用
   */
  @SuppressWarnings("unchecked")
  public Stream<T> stream() {
    Spliterator<T> spliterator =
        Spliterators.spliteratorUnknownSize(
            new ExcelIterator<>(buffer, completion, error, isClosed),
            Spliterator.NONNULL | Spliterator.IMMUTABLE);
    return StreamSupport.stream(spliterator, false).onClose(this::close);
  }

  /**
   * 阻塞等待读取完成。
   *
   * @return 当前实例，可继续操作
   * @throws ExcelReadException 读取过程中发生异常
   */
  public ExcelStream<T> awaitCompletion() {
    try {
      completion.get();
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ExcelReadException(ExcelExceptionCode.READ_CANCELLED, "await 中断", ie);
    } catch (ExecutionException ee) {
      throw new ExcelReadException(
          ExcelExceptionCode.IO_ERROR, "读取异常", ee.getCause());
    }
    Throwable t = error.get();
    if (t != null) {
      throw new ExcelReadException(ExcelExceptionCode.IO_ERROR, "读取过程出错", t);
    }
    return this;
  }

  @Override
  public void close() {
    if (isClosed.compareAndSet(false, true)) {
      isClosed.set(true);
      completion.cancel(true);
      LOG.debug("ExcelStream 已关闭");
      // 清空缓冲区
      buffer.clear();
    }
  }
}

/**
 * 将 BlockingQueue 桥接为 {@link java.util.Iterator}，供 Stream Spliterator 消费。
 *
 * <p>消费端调用 {@link #next()} 时：若缓冲区空且未结束，阻塞等待；若遇到结束标记，返回 null 并通知流结束。
 *
 * @param <T> 元素类型
 */
class ExcelIterator<T> implements java.util.Iterator<T> {

  private static final Logger LOG = LoggerFactory.getLogger(ExcelIterator.class);
  private static final long POLL_TIMEOUT_MS = 500;

  private final BlockingQueue<Object> buffer;
  private final CompletableFuture<Void> completion;
  private final AtomicReference<Throwable> error;
  private final AtomicBoolean isClosed;
  private Object nextElement = null;
  private boolean finished = false;

  ExcelIterator(
      BlockingQueue<Object> buffer,
      CompletableFuture<Void> completion,
      AtomicReference<Throwable> error,
      AtomicBoolean isClosed) {
    this.buffer = buffer;
    this.completion = completion;
    this.error = error;
    this.isClosed = isClosed;
  }

  @Override
  public boolean hasNext() {
    if (finished) {
      return false;
    }
    if (nextElement != null) {
      return true;
    }
    try {
      while (!isClosed.get()) {
        nextElement = buffer.poll(POLL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (nextElement == ExcelStream.END_SENTINEL) {
          finished = true;
          return false;
        }
        if (nextElement != null) {
          return true;
        }
        // 空且未结束：检查是否有异常
        if (error.get() != null) {
          Throwable t = error.get();
          LOG.error("ExcelIterator 遇到异常", t);
          throw new ExcelReadException(ExcelExceptionCode.IO_ERROR, "读取过程出错", t);
        }
      }
      finished = true;
      return false;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ExcelReadException(ExcelExceptionCode.READ_CANCELLED, "迭代中断", ie);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T next() {
    if (!hasNext() && finished) {
      throw new java.util.NoSuchElementException("Excel 数据已耗尽");
    }
    T result = (T) nextElement;
    if (result == null) {
      throw new java.util.NoSuchElementException("Excel 数据已耗尽");
    }
    nextElement = null;
    return result;
  }
}
