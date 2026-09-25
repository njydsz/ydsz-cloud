package com.njydsz.common.excel.core.reactive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 响应式输出流 — 桥接同步 Excel XML/ZIP 写入到 {@link Flow.Publisher}&lt;byte[]&gt;，适配 WebFlux {@code Flux<byte[]>}。
 *
 * <p>设计思路：将 xlsx 的几个部分（Content Types、Rels、Workbook、Sheet XML、Styles、SST）任其一
 * 写入到本流时立即触发 {@code onNext(byte[])} 推送给订阅者。由于 xlsx 本质是 ZIP 随机访问格式，
 * 无法做到纯流式 ZIP 输出；本流以"段落推送"模式工作（写入一个段 → 推送一个 byte[] 批次），最终
 * {@code close()} 时发出 {@code onComplete}。
 *
 * <p>背压实现：通过计数 + 简单拉取模式 — 当下游 Subscription.request(n) 返回后生产者才继续推送；
 * 若下游未发出请求，生产者自旋等待（含超时保护）。适用于 WebFlux 的内置背压机制（默认 unbounded）。
 *
 * <h3>使用示例（配合 reactor-core，可选）</h3>
 *
 * <pre>{@code
 * ReactiveOutputStream ros = new ReactiveOutputStream();
 * // reactor-core 可选桥接
 * Flux<byte[]> flux = Flux.from(ros);
 *
 * // 另一个线程写 Excel 数据到 ros（写一点推一点）
 * new Thread(() -> {
 *     try {
 *         ros.write(sheetXmlBytes);
 *         ros.write(sharedStringsXmlBytes);
 *         ros.close(); // 触发 onComplete
 *     } catch (IOException e) {
 *         // handle
 *     }
 * }).start();
 *
 * // WebFlux 控制器直接返回 flux
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public class ReactiveOutputStream extends OutputStream implements Flow.Publisher<byte[]> {

  /** 默认段落大小（byte）— 当写入不频繁时，flushThreshold 内的数据会合并为一次推送 */
  public static final int DEFAULT_FLUSH_THRESHOLD = 8192;

  /** 默认最长等待下游请求的超时（ms），避免永久阻塞 */
  public static final long DEFAULT_REQUEST_TIMEOUT_MS = 30_000;

  private final int flushThreshold;
  private final long requestTimeoutMs;

  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicLong requested = new AtomicLong(0);

  private Flow.Subscriber<? super byte[]> subscriber;
  private final java.io.ByteArrayOutputStream buffer;
  private volatile boolean closed = false;

  public ReactiveOutputStream() {
    this(DEFAULT_FLUSH_THRESHOLD, DEFAULT_REQUEST_TIMEOUT_MS);
  }

  /**
   * @param flushThreshold 缓冲区达到此大小时触发一次推送（合并小写入）
   * @param requestTimeoutMs 等待下游请求的超时（ms），超时后抛出 RuntimeException
   */
  public ReactiveOutputStream(int flushThreshold, long requestTimeoutMs) {
    this.flushThreshold = flushThreshold;
    this.requestTimeoutMs = requestTimeoutMs;
    this.buffer = new java.io.ByteArrayOutputStream(flushThreshold);
  }

  /**
   * 订阅 — 根据 Reactive Streams 规范 {@code Publisher.subscribe}。
   */
  @Override
  public void subscribe(Flow.Subscriber<? super byte[]> subscriber) {
    lock.lock();
    try {
      if (this.subscriber != null) {
        subscriber.onError(
            new IllegalStateException("ReactiveOutputStream does not support multiple subscribers"));
        return;
      }
      this.subscriber = subscriber;
      subscriber.onSubscribe(createSubscription());
    } finally {
      lock.unlock();
    }
  }

  private Flow.Subscription createSubscription() {
    return new Flow.Subscription() {
      @Override
      public void request(long n) {
        if (n <= 0) {
          // Reactive Streams 3.9 规范：非法参数 → onError
          subscriber.onError(new IllegalArgumentException("Non-positive request: " + n));
          return;
        }
        requested.addAndGet(n);
      }

      @Override
      public void cancel() {
        closed = true;
      }
    };
  }

  @Override
  public void write(int b) throws IOException {
    lock.lock();
    try {
      buffer.write(b);
      if (buffer.size() >= flushThreshold) {
        flushBuffer();
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    lock.lock();
    try {
      buffer.write(b, off, len);
      if (buffer.size() >= flushThreshold) {
        flushBuffer();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * 缓冲区已满或 onClose 时推送批次数据给订阅者。
   *
   * <p>如果当前 requested 计数 == 0，等待下游请求到达（超时保护）。
   */
  private void flushBuffer() throws IOException {
    if (buffer.size() == 0) {
      return;
    }
    waitForRequestIfNeeded();
    byte[] chunk = buffer.toByteArray();
    buffer.reset();
    requested.decrementAndGet();

    if (subscriber != null) {
      try {
        subscriber.onNext(chunk);
      } catch (Exception e) {
        throw new IOException("Subscriber.onNext failed", e);
      }
    }
  }

  private void waitForRequestIfNeeded() {
    long deadline = System.currentTimeMillis() + requestTimeoutMs;
    while (requested.get() <= 0 && !closed && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (requested.get() <= 0 && !closed) {
      throw new RuntimeException(
          "ReactiveOutputStream timeout: downstream did not send request within "
              + requestTimeoutMs
              + "ms");
    }
  }

  @Override
  public void flush() throws IOException {
    lock.lock();
    try {
      flushBuffer();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    lock.lock();
    try {
      if (closed) {
        return;
      }
      closed = true;
      flushBuffer();
      if (subscriber != null) {
        subscriber.onComplete();
      }
    } finally {
      lock.unlock();
    }
  }
}
