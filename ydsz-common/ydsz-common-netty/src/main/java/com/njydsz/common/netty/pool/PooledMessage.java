package com.njydsz.common.netty.pool;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;

/**
 * 可池化消息基类，基于 Netty {@link Recycler} 实现对象复用。
 *
 * <p>适用于高频创建/销毁的消息对象（如 RPC 请求/响应、ACK 消息等），通过线程本地栈（Stack）减少 GC 压力。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * public class MyMessage extends PooledMessage&lt;MyMessage&gt; {
 *     private String content;
 *
 *     public static MyMessage newInstance() {
 *         return RECYCLER.get();
 *     }
 *
 *     public MyMessage setContent(String content) {
 *         this.content = content;
 *         return this;
 *     }
 *
 *     public String getContent() { return content; }
 *
 *     &#64;Override
 *     public void reset() {
 *         this.content = null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意：</b>
 *
 * <ul>
 *   <li>使用完毕后必须调用 {@link #recycle()} 归还对象，否则池不会自动回收</li>
 *   <li>Recycler 是线程本地的，跨线程使用可能导致对象泄漏</li>
 *   <li>池化对象不要持有长生命周期的大对象引用（如文件句柄、数据库连接）</li>
 * </ul>
 *
 * @param <T> 子类型自身（自引用泛型）
 * @author ydsz-team
 * @since 26.09.01
 */
public abstract class PooledMessage<T extends PooledMessage<T>> {

  /** 默认最大线程本地容量（每个线程栈中保留的对象数） */
  private static final int DEFAULT_MAX_CAPACITY = 262144;

  /** 关联的 Recycler 句柄 */
  // YDIZ-WARN-001 允许保留：消息缓冲池泛型擦除，调用方承担转型
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Handle<T> handle;

  /**
   * 构造池化消息（由子类的 {@code newInstance} 工厂方法通过 {@link Recycler#get()} 调用）。
   *
   * @param handle Recycler 句柄
   */
  protected PooledMessage(Handle<T> handle) {
    this.handle = handle;
  }

  /**
   * 归还对象到池中。
   *
   * <p>调用后<strong>不可再使用此对象</strong>，所有引用应置为 null。
   */
  // YDIZ-WARN-001 允许保留：消息体反序列化原始类型，由具体消息类限定
  @SuppressWarnings("unchecked")
  public final void recycle() {
    reset();
    handle.recycle((T) this);
  }

  /**
   * 重置对象状态（子类覆写以清除字段值）。
   *
   * <p>在对象归还到池前自动调用，确保下次获取到干净状态。
   */
  public void reset() {
    // 默认空实现，子类按需覆写
  }

  /**
   * 创建允许子类覆盖的 Recycler（子类必须提供此静态工厂）。
   *
   * <p>子类示例：
   *
   * <pre>{@code
   * private static final Recycler&lt;MyMessage&gt; RECYCLER = new Recycler&lt;MyMessage&gt;() {
   *     &#64;Override
   *     protected MyMessage newObject(Handle&lt;MyMessage&gt; handle) {
   *         return new MyMessage(handle);
   *     }
   * };
   * }</pre>
   *
   * @param <R> 子类型
   * @return Recycler 实例
   */
  protected static <R extends PooledMessage<R>> Recycler<R> createRecycler() {
    return new Recycler<R>(DEFAULT_MAX_CAPACITY) {
      @Override
      protected R newObject(Handle<R> handle) {
        throw new UnsupportedOperationException("子类必须覆写此方法");
      }
    };
  }

  /**
   * 创建自定义最大容量的 Recycler（子类必须提供此静态工厂）。
   *
   * @param maxCapacityPerThread 每个线程的最大容量
   * @param <R> 子类型
   * @return Recycler 实例
   */
  protected static <R extends PooledMessage<R>> Recycler<R> createRecycler(int maxCapacityPerThread) {
    return new Recycler<R>(maxCapacityPerThread) {
      @Override
      protected R newObject(Handle<R> handle) {
        throw new UnsupportedOperationException("子类必须覆写此方法");
      }
    };
  }
}
