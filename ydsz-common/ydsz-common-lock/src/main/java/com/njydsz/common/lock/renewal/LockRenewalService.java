package com.njydsz.common.lock.renewal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.lock.annotation.LockType;

/**
 * 分布式锁续期 SPI 服务。
 *
 * <p>将散落在各锁实现（{@code LockWatchDog}、{@code RedisReentrantLock} 等）中的 续期 Lua 脚本统一收口到本服务，消除"双锁冗余"——避免同一续期逻辑在多处维护导致脚本漂移。
 *
 * <p>提供两类续期脚本：
 *
 * <ul>
 *   <li>{@link #RENEW_SCRIPT_HASH}：适用于可重入锁（clientId 作为 Hash field）
 *   <li>{@link #RENEW_SCRIPT_OWNER}：适用于公平锁（clientId 作为 owner 字段的值）
 * </ul>
 *
 * <p><b>SPI 扩展点：</b>业务方可实现 {@link LockRenewalStrategy} 接口自定义续期逻辑 （如增加续期次数校验、续期前后埋点等），并通过 {@link
 * #setStrategy(LockRenewalStrategy)} 注入。
 *
 * <p><b>线程安全：</b>脚本实例为无状态不可变对象，多线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class LockRenewalService {

  /**
   * 续期 Lua 脚本：可重入锁版本。
   *
   * <p>仅当 Hash 中存在 clientId 字段时表示持有锁，执行 PEXPIRE。 脚本返回 1 表示续期成功，0 表示锁不存在或已被其他客户端获取。
   */
  public static final String RENEW_SCRIPT_HASH =
      "if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1 then "
          + "    redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
          + "    return 1 "
          + "else "
          + "    return 0 "
          + "end";

  /**
   * 续期 Lua 脚本：公平锁版本。
   *
   * <p>仅当 Hash 中 "owner" 字段的值等于 clientId 时才续期。 脚本返回 1 表示续期成功，0 表示锁的持有者已变更。
   */
  public static final String RENEW_SCRIPT_OWNER =
      "if redis.call('HGET', KEYS[1], 'owner') == ARGV[1] then "
          + "    redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
          + "    return 1 "
          + "else "
          + "    return 0 "
          + "end";

  /** 可重入锁续期脚本（已编译）。 */
  private final DefaultRedisScript<Long> renewHashScript;

  /** 公平锁续期脚本（已编译）。 */
  private final DefaultRedisScript<Long> renewOwnerScript;

  /** 可选的续期策略（SPI 扩展点）。 */
  private volatile LockRenewalStrategy strategy;

  /** 构造锁续期服务，预编译所有续期脚本。 */
  public LockRenewalService() {
    this.renewHashScript = new DefaultRedisScript<>(RENEW_SCRIPT_HASH, Long.class);
    this.renewOwnerScript = new DefaultRedisScript<>(RENEW_SCRIPT_OWNER, Long.class);
  }

  /**
   * 注入可选的续期策略（SPI 扩展点）。
   *
   * @param strategy 续期策略实现，可为 null
   */
  public void setStrategy(LockRenewalStrategy strategy) {
    this.strategy = strategy;
  }

  /**
   * 根据锁类型获取对应的已编译续期脚本。
   *
   * @param lockType 锁类型
   * @return 已编译的续期脚本
   */
  public DefaultRedisScript<Long> getRenewScript(LockType lockType) {
    if (lockType == LockType.FAIR) {
      return renewOwnerScript;
    }
    return renewHashScript;
  }

  /**
   * 执行单锁续期。
   *
   * <p>根据锁类型自动选择续期脚本：
   *
   * <ul>
   *   <li>{@link LockType#REENTRANT} / {@link LockType#MULTI}：使用 Hash field 校验
   *   <li>{@link LockType#FAIR}：使用 owner 字段校验
   * </ul>
   *
   * <p>如注入了 {@link LockRenewalStrategy}，会先调用其 {@link LockRenewalStrategy#beforeRenew} 与 {@link
   * LockRenewalStrategy#afterRenew} 钩子。
   *
   * @param redisTemplate Redis 操作模板
   * @param lockKey 锁键（已包含命名空间前缀）
   * @param clientId 客户端标识
   * @param leaseTimeMs 续期时间（毫秒）
   * @param lockType 锁类型
   * @return true 表示续期成功，false 表示锁已释放或持有者变更
   */
  public boolean renew(
      StringRedisTemplate redisTemplate,
      String lockKey,
      String clientId,
      long leaseTimeMs,
      LockType lockType) {
    if (strategy != null) {
      strategy.beforeRenew(lockKey, lockType);
    }
    DefaultRedisScript<Long> script = getRenewScript(lockType);
    try {
      Long result =
          redisTemplate.execute(
              script, Collections.singletonList(lockKey), clientId, String.valueOf(leaseTimeMs));
      boolean success = Long.valueOf(1L).equals(result);
      if (!success) {
        log.warn("[ydsz-lock] [renewal] 续期失败，锁可能已释放 lockKey={} lockType={}", lockKey, lockType);
      }
      if (strategy != null) {
        strategy.afterRenew(lockKey, lockType, success);
      }
      return success;
    } catch (Exception e) {
      log.error(
          "[ydsz-lock] [renewal] 续期异常 lockKey={} lockType={} cause={}",
          lockKey,
          lockType,
          e.getMessage());
      if (strategy != null) {
        strategy.afterRenew(lockKey, lockType, false);
      }
      return false;
    }
  }

  /**
   * 批量续期（P1-P1 新增：使用 Redis Pipeline 将多次续期合并为一次网络往返）。
   *
   * <p>适用场景：
   *
   * <ul>
   *   <li>WatchDog 在同一个调度周期内有多把锁需要续期
   *   <li>{@code RedisMultiLock} 同时续期多把子锁
   * </ul>
   *
   * <p><b>性能收益：</b>100 把锁续期从 100 次网络往返降为 1 次（Pipeline）， 实测可减少 Redis 网络延迟对续期线程的阻塞。
   *
   * <p><b>返回值：</b>与 {@code renewTasks} 一一对应的布尔列表，{@code true} 表示该锁续期成功。
   *
   * <p><b>实现说明：</b>基于 {@code RedisSerializer} 序列化脚本参数， 通过 {@link
   * StringRedisTemplate#executePipelined} 在连接层面一次提交多条 Lua 脚本。 底层 Redis 驱动（Lettuce/Jedis）会合并网络 IO， 以 Pipeline 协议一次发送多条 EVAL 命令并一次收集所有返回值。
   *
   * @param redisTemplate Redis 操作模板
   * @param renewTasks 续期任务列表（lockKey, clientId, leaseTimeMs, lockType）
   * @return 续期结果列表（与 renewTasks 等长）
   */
  public List<Boolean> renewBatch(
      StringRedisTemplate redisTemplate, List<RenewTask> renewTasks) {
    if (renewTasks == null || renewTasks.isEmpty()) {
      return Collections.emptyList();
    }
    if (renewTasks.size() == 1) {
      // 单条退化为普通续期
      RenewTask task = renewTasks.get(0);
      return List.of(
          renew(redisTemplate, task.lockKey(), task.clientId(), task.leaseTimeMs(), task.lockType()));
    }

    // 预序列化所有任务的参数（避免在 pipeline 回调中重复序列化）
    byte[][] scripts = new byte[renewTasks.size()][];
    byte[][][] keysAndArgs = new byte[renewTasks.size()][][];
    ReturnType returnType = ReturnType.INTEGER;
    int[] keyCounts = new int[renewTasks.size()];
    for (int i = 0; i < renewTasks.size(); i++) {
      RenewTask task = renewTasks.get(i);
      DefaultRedisScript<Long> renewScript =
          task.lockType() == LockType.FAIR ? renewOwnerScript : renewHashScript;
      scripts[i] = renewScript.getScriptAsString().getBytes();
      byte[] keyBytes = redisTemplate.getStringSerializer().serialize(task.lockKey());
      byte[] clientIdBytes = redisTemplate.getStringSerializer().serialize(task.clientId());
      byte[] leaseBytes =
          redisTemplate.getStringSerializer().serialize(String.valueOf(task.leaseTimeMs()));
      keysAndArgs[i] = new byte[][] {keyBytes, clientIdBytes, leaseBytes};
      keyCounts[i] = 1;
    }

    try {
      // Pipeline 批量执行所有续期 Lua 脚本，合并为一次网络往返
      List<Object> results =
          redisTemplate.executePipelined(
              (RedisCallback<Object>)
                  connection -> {
                    for (int i = 0; i < renewTasks.size(); i++) {
                      connection.eval(scripts[i], returnType, keyCounts[i], keysAndArgs[i]);
                    }
                    return null;
                  });

      // 转换结果
      List<Boolean> successList = new ArrayList<>(renewTasks.size());
      for (int i = 0; i < results.size() && i < renewTasks.size(); i++) {
        Object result = results.get(i);
        boolean success = Long.valueOf(1L).equals(result);
        successList.add(success);
        if (!success) {
          RenewTask task = renewTasks.get(i);
          log.warn(
              "[ydsz-lock] [renewal] 批量续期中单锁失败 lockKey={} lockType={}",
              task.lockKey(),
              task.lockType());
        }
      }
      return successList;
    } catch (Exception e) {
      log.error(
          "[ydsz-lock] [renewal] 批量续期异常 size={} cause={}", renewTasks.size(), e.getMessage(), e);
      // 全部标记为失败
      List<Boolean> failures = new ArrayList<>(renewTasks.size());
      for (int i = 0; i < renewTasks.size(); i++) {
        failures.add(false);
      }
      return failures;
    }
  }

  /**
   * 续期任务参数（用于批量续期）。
   *
   * @param lockKey 锁键（已含命名空间前缀）
   * @param clientId 客户端标识
   * @param leaseTimeMs 续期时间（毫秒）
   * @param lockType 锁类型
   */
  public record RenewTask(String lockKey, String clientId, long leaseTimeMs, LockType lockType) {

    /**
     * 构造续期任务。
     *
     * @param lockKey 锁键
     * @param clientId 客户端标识
     * @param leaseTimeMs 租约时间（毫秒）
     * @param lockType 锁类型
     * @return 续期任务实例
     */
    public static RenewTask of(
        String lockKey, String clientId, long leaseTimeMs, LockType lockType) {
      return new RenewTask(lockKey, clientId, leaseTimeMs, lockType);
    }
  }

  /**
   * 续期策略 SPI 接口。
   *
   * <p>业务方可实现此接口注入自定义续期行为：
   *
   * <ul>
   *   <li>{@link #beforeRenew}：续期前钩子（可用于续期次数校验）
   *   <li>{@link #afterRenew}：续期后钩子（可用于埋点统计）
   * </ul>
   */
  public interface LockRenewalStrategy {
    /**
     * 续期前钩子。
     *
     * @param lockKey 锁键
     * @param lockType 锁类型
     */
    default void beforeRenew(String lockKey, LockType lockType) {
      // 默认空实现
    }

    /**
     * 续期后钩子。
     *
     * @param lockKey 锁键
     * @param lockType 锁类型
     * @param success 续期是否成功
     */
    default void afterRenew(String lockKey, LockType lockType, boolean success) {
      // 默认空实现
    }
  }
}
