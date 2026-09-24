package com.njydsz.common.seata.aspect;

import com.njydsz.common.seata.annotation.YdszGlobalTransactional;
import com.njydsz.common.util.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Seata 全局事务反射桥接器。
 *
 * <p>该对象是本模块中允许直接（间接）引用 Seata 全局事务 API 的唯一入口，
 * 所有对 {@code io.seata.tm.api.GlobalTransaction} /
 * {@code io.seata.core.context.RootContext} 的反射调用均收口于此，便于后续引入
 * ArchUnit 规则（YDIZ-COMMON-003）约束"反射调用 Seata RootContext 的代码仅允许
 * 出现在 ydzs-common-seata 模块内部"。
 *
 * <p>全局事务生命周期：
 * <ol>
 *   <li>{@link #beginGlobalTx} — 通过 {@code GlobalTransactionContext.getCurrentOrCreate()}
 *       开启全局事务；</li>
 *   <li>{@link #commit()} — 当标注方法正常返回；</li>
 *   <li>{@link #rollback()} — 当标注方法抛出应回滚异常。</li>
 * </ol>
 *
 * @author ydsz-team
 * @since ACC-1
 * @see com.njydsz.common.seata.aspect.YdszGlobalTransactionalAspect
 */
@Slf4j
public final class SeataReflector {

  private static final String GLOBAL_TXN_CLASS = "io.seata.tm.api.GlobalTransaction";
  private static final String ROOT_CONTEXT_CLASS = "io.seata.core.context.RootContext";
  private static final String GLOBAL_TXN_CONTEXT_CLASS = "io.seata.tm.api.GlobalTransactionContext";

  private SeataReflector() {
    // 工具类，禁止实例化
  }

  /**
   * Seata 运行时是否存在于 classpath（即业务方是否引入了 seata 客户端）。
   */
  public static boolean seataPresent() {
    try {
      Class.forName(ROOT_CONTEXT_CLASS);
      Class.forName(GLOBAL_TXN_CLASS);
      return true;
    } catch (ClassNotFoundException e) {
      log.debug("Seata 客户端不在 classpath: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 开启提交全局事务。
   *
   * <p>通过反射调用 {@code io.seata.tm.api.TransactionManager}（由 Spring 容器注册），
   * 在其钩子内创建并启动由 SeataAutoConfiguration 扫描到的全局事务，返回开启后的 XID。
   *
   * @return 已开启的 XID，若无法开启则返回 {@code null}
   */
  public static String beginGlobalTx(
      final ProceedingJoinPoint joinPoint, final YdszGlobalTransactional ann) {
    try {
      // 取得业务方法的 GlobalTransaction，由 @YdszGlobalTransactionalAspect 扫描注册
      final Class<?> globalTxnClass = Class.forName(GLOBAL_TXN_CLASS);
      final Class<?> globalTxnCtxClass = Class.forName(GLOBAL_TXN_CONTEXT_CLASS);
      final Method getCurrentOrCreate =
          globalTxnCtxClass.getMethod("getCurrentOrCreate");
      final Object globalTx = getCurrentOrCreate.invoke(null);
      final String name = Optional.ofNullable(ann.name()).filter(s -> !s.isEmpty())
          .orElseGet(() -> joinPoint.getSignature().toShortString());
      final Method begin = globalTxnClass.getMethod("begin", int.class, String.class);
      begin.invoke(globalTx, ann.timeoutMills(), name);
      return getXid();
    } catch (Exception e) {
      log.warn(
          "开启全局事务失败，将由上层 @YdszGlobalTransactionalAspect 决定是否放行; method={}",
          joinPoint.getSignature().toShortString(),
          e);
      return null;
    }
  }

  /** 提交全局事务 */
  public static void commit() {
    try {
      final Class<?> globalTxnCtxClass = Class.forName(GLOBAL_TXN_CONTEXT_CLASS);
      final Method getCurrent = globalTxnCtxClass.getMethod("getCurrent");
      final Object globalTx = getCurrent.invoke(null);
      if (globalTx != null) {
        final Class<?> globalTxnClass = Class.forName(GLOBAL_TXN_CLASS);
        final Method commit = globalTxnClass.getMethod("commit");
        commit.invoke(globalTx);
        log.debug("Seata 全局事务已提交");
      }
    } catch (Exception e) {
      log.warn("提交全局事务异常", e);
    }
  }

  /** 回滚全局事务 */
  public static void rollback() {
    try {
      final Class<?> globalTxnCtxClass = Class.forName(GLOBAL_TXN_CONTEXT_CLASS);
      final Method getCurrent = globalTxnCtxClass.getMethod("getCurrent");
      final Object globalTx = getCurrent.invoke(null);
      if (globalTx != null) {
        final Class<?> globalTxnClass = Class.forName(GLOBAL_TXN_CLASS);
        final Method rollback = globalTxnClass.getMethod("rollback");
        rollback.invoke(globalTx);
        log.debug("Seata 全局事务已回滚");
      }
    } catch (Exception e) {
      log.warn("回滚全局事务异常", e);
    }
  }

  /**
   * 取得当前线程的全局事务 XID（用于 Feign 调用链透传）。
   *
   * @return XID，若当前不在全局事务上下文中返回 {@code null}
   */
  public static String getXid() {
    try {
      final Class<?> rootContextClass = Class.forName(ROOT_CONTEXT_CLASS);
      final Method getXid = rootContextClass.getMethod("getXID");
      return (String) getXid.invoke(null);
    } catch (Exception e) {
      log.debug("获取 Seata XID 失败（当前可能未处于全局事务上下文）", e);
      return null;
    }
  }

  /**
   * 将 XID 绑定到当前线程的全局事务上下文（服务端入参解析用）。
   */
  public static void bindXid(final String xid) {
    if (xid == null || xid.isEmpty()) {
      return;
    }
    try {
      final Class<?> rootContextClass = Class.forName(ROOT_CONTEXT_CLASS);
      final Method bind = rootContextClass.getMethod("bind", String.class);
      bind.invoke(null, xid);
    } catch (Exception e) {
      log.warn("绑定 Seata XID 失败, xid={}", xid, e);
    }
  }

  /** 解绑当前线程的 XID（请求完成后调用） */
  public static void unbindXid() {
    try {
      final Class<?> rootContextClass = Class.forName(ROOT_CONTEXT_CLASS);
      final Method unbind = rootContextClass.getMethod("unbind");
      unbind.invoke(null);
    } catch (Exception e) {
      log.warn("解绑 Seata XID 失败", e);
    }
  }
}
