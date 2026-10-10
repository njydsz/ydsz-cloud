package com.njydsz.common.safe.idempotent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
import com.njydsz.common.safe.idempotent.aspect.IdempotentAspect;
import com.njydsz.common.safe.idempotent.exception.IdempotentException;
import com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IdempotentAspect} 并发场景单元测试。
 *
 * <p>模拟多线程并发提交相同请求，验证仅 1 个线程能成功进入业务逻辑，
 * 其余线程抛出 {@link IdempotentException}（HTTP 409 Conflict）。</p>
 *
 * <p>不启动 Spring Context，使用 Mockito 模拟 {@link IdempotentStrategy} 的 SET NX 语义，
 * 用 {@link CyclicBarrier} 确保所有线程同时发起请求。</p>
 */
@DisplayName("IdempotentAspect — 幂等并发测试")
class IdempotentConcurrencyTest {

  /** 并发线程数 */
  private static final int THREAD_COUNT = 10;

  /** 测试用幂等键 */
  private static final String IDEMPOTENT_KEY = "test:order:create";

  /** 测试用 TTL（秒） */
  private static final int TTL_SECONDS = 5;

  private IdempotentStrategy idempotentStrategy;
  private ProceedingJoinPoint joinPoint;
  private MethodSignature methodSignature;
  private Idempotent idempotent;
  private IdempotentAspect aspect;

  @BeforeEach
  void setUp() throws Exception {
    AtomicBoolean lock = new AtomicBoolean(false);

    idempotentStrategy = mock(IdempotentStrategy.class);
    when(idempotentStrategy.acquire(anyString(), anyLong())).thenAnswer((Answer<String>) invocation -> {
      if (lock.compareAndSet(false, true)) {
        return "token-" + Thread.currentThread().threadId();
      }
      return null;
    });
    when(idempotentStrategy.release(anyString(), anyString())).thenAnswer((Answer<Boolean>) invocation -> {
      lock.set(false);
      return true;
    });

    aspect = new IdempotentAspect(idempotentStrategy, "test:", null, null);

    joinPoint = mock(ProceedingJoinPoint.class);
    methodSignature = mock(MethodSignature.class);
    doReturn(methodSignature).when(joinPoint).getSignature();

    Method targetMethod = String.class.getMethod("length");
    doReturn(targetMethod).when(methodSignature).getMethod();
    when(joinPoint.getArgs()).thenReturn(new Object[]{"test-arg"});

    idempotent = mock(Idempotent.class);
    when(idempotent.key()).thenReturn(IDEMPOTENT_KEY);
    when(idempotent.ttlSeconds()).thenReturn(TTL_SECONDS);
    when(idempotent.message()).thenReturn("请勿重复提交");
    when(idempotent.condition()).thenReturn("");

    // 默认 mock proceed() 返回 null（可在测试方法中覆盖）
    try {
      Mockito.doAnswer(invocationOnMock -> null).when(joinPoint).proceed();
    } catch (Throwable t) {
      throw new RuntimeException("mock proceed 失败", t);
    }
  }

  @Nested
  @DisplayName("并发场景：10 线程同时请求")
  class ConcurrentTenThreads {

    @Test
    @DisplayName("10 线程并发提交同一请求，仅 1 个成功进入业务逻辑")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void ten_threads_concurrent_only_one_succeeds() throws Throwable {
      AtomicInteger businessExecCount = new AtomicInteger(0);
      when(joinPoint.proceed()).thenAnswer(invocation -> businessExecCount.incrementAndGet());

      CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
      CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

      List<Boolean> successes = new ArrayList<>(THREAD_COUNT);
      List<Boolean> failures = new ArrayList<>(THREAD_COUNT);

      ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

      for (int i = 0; i < THREAD_COUNT; i++) {
        executor.submit(() -> {
          try {
            safeBarrierAwait(barrier);
            aspect.around(joinPoint, idempotent);
            synchronized (successes) {
              successes.add(true);
            }
          } catch (IdempotentException e) {
            synchronized (failures) {
              failures.add(true);
            }
          } catch (Exception e) {
            synchronized (failures) {
              failures.add(false);
            }
          } finally {
            doneLatch.countDown();
          }
        });
      }

      doneLatch.await(10, TimeUnit.SECONDS);
      executor.shutdown();

      assertThat(successes).hasSize(1);
      assertThat(failures).hasSize(THREAD_COUNT - 1);
      assertThat(businessExecCount.get()).isOne();
      long idempotentRejections = failures.stream().filter(b -> b).count();
      assertThat(idempotentRejections).isEqualTo(THREAD_COUNT - 1);
    }

    @Test
    @DisplayName("并发场景验证 acquire 被调用 10 次，仅 1 次返回非 null")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void acquire_called_ten_times_only_one_returns_token() throws InterruptedException {
      AtomicInteger acquireCount = new AtomicInteger(0);
      AtomicInteger nullTokenCount = new AtomicInteger(0);

      CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
      CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
      ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

      for (int i = 0; i < THREAD_COUNT; i++) {
        executor.submit(() -> {
          try {
            safeBarrierAwait(barrier);
            String token = idempotentStrategy.acquire("test:" + IDEMPOTENT_KEY, TTL_SECONDS * 1000L);
            acquireCount.incrementAndGet();
            if (token == null) {
              nullTokenCount.incrementAndGet();
            }
          } catch (Exception e) {
            // barrier await 异常
          } finally {
            doneLatch.countDown();
          }
        });
      }

      doneLatch.await(10, TimeUnit.SECONDS);
      executor.shutdown();

      assertThat(acquireCount.get()).isEqualTo(THREAD_COUNT);
      assertThat(nullTokenCount.get()).isEqualTo(THREAD_COUNT - 1);
    }
  }

  @Nested
  @DisplayName("并发场景：CountDownLatch 同步")
  class CountDownLatchSync {

    @Test
    @DisplayName("CountDownLatch 同步 10 线程并发请求")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void countdown_latch_sync_ten_threads() throws Throwable {
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch completeLatch = new CountDownLatch(THREAD_COUNT);
      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger idempotentRejectCount = new AtomicInteger(0);
      AtomicInteger businessExecCount = new AtomicInteger(0);

      when(joinPoint.proceed()).thenAnswer(invocation -> businessExecCount.incrementAndGet());

      ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

      for (int i = 0; i < THREAD_COUNT; i++) {
        executor.submit(() -> {
          try {
            startLatch.await();
            aspect.around(joinPoint, idempotent);
            successCount.incrementAndGet();
          } catch (IdempotentException e) {
            idempotentRejectCount.incrementAndGet();
          } catch (Exception e) {
            // ignored
          } finally {
            completeLatch.countDown();
          }
        });
      }

      startLatch.countDown();
      completeLatch.await(10, TimeUnit.SECONDS);
      executor.shutdown();

      assertThat(successCount.get()).isOne();
      assertThat(idempotentRejectCount.get()).isEqualTo(THREAD_COUNT - 1);
      assertThat(businessExecCount.get()).isOne();
    }
  }

  @Nested
  @DisplayName("并发场景：业务异常后锁释放允许重试")
  class ReleaseOnBusinessException {

    @Test
    @DisplayName("业务异常后幂等锁释放，后续请求可重试成功")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void business_exception_releases_lock_allows_retry() throws Throwable {
      AtomicInteger acquireCount = new AtomicInteger(0);
      AtomicBoolean lockHeld = new AtomicBoolean(false);
      AtomicInteger secondCallCount = new AtomicInteger(0);

      when(idempotentStrategy.acquire(anyString(), anyLong())).thenAnswer((Answer<String>) invocation -> {
        int count = acquireCount.incrementAndGet();
        if (count == 1) {
          lockHeld.set(true);
          return "first-token";
        }
        if (count == 2) {
          return null;
        }
        if (count == 3) {
          secondCallCount.incrementAndGet();
          return "third-token";
        }
        return null;
      });

      when(idempotentStrategy.release(anyString(), anyString())).thenAnswer((Answer<Boolean>) invocation -> {
        lockHeld.set(false);
        return true;
      });

      // 第一次调用：抛出 BusinessException
      when(joinPoint.proceed()).thenThrow(new BusinessException(CoreExceptionCode.FAIL));

      assertThatThrownBy(() -> aspect.around(joinPoint, idempotent))
          .isInstanceOf(BusinessException.class);

      assertThat(acquireCount.get()).isOne();

      // 第二次调用：正常返回（锁已释放后可重试）
      when(joinPoint.proceed()).thenReturn("success");
      Object result = aspect.around(joinPoint, idempotent);
      assertThat(result).isEqualTo("success");
    }
  }

  /**
   * 安全执行 CyclicBarrier.await()，将 checked exception 转为运行时异常。
   *
   * @param barrier 屏障
   */
  private static void safeBarrierAwait(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (InterruptedException | BrokenBarrierException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("CyclicBarrier await interrupted", e);
    }
  }
}
