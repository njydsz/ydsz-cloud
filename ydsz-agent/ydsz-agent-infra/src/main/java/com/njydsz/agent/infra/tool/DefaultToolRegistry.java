package com.njydsz.agent.infra.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.njydsz.common.json.YdszJson;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.tool.ToolExecutor;
import com.njydsz.agent.domain.tool.ToolRegistration;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.common.json.YdszJson;

/**
 * 默认工具注册中心实现
 *
 * <p>使用 {@link ConcurrentHashMap} 存储工具注册条目，线程安全。 支持编程式注册和注解扫描注册（通过 {@code ToolAnnotationScanner}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DefaultToolRegistry implements ToolRegistry {

  /** 工具注册表（key=工具名） */
  private final Map<String, ToolRegistration> registry = new ConcurrentHashMap<>();

  /** 工具执行超时（秒），0 表示不限时 */
  private final int defaultTimeoutSeconds;

  /** 工具执行线程池（JDK 21 虚拟线程，规范豁免场景） */
  // CHECKSTYLE.OFF: RegexpSinglelineJava - 虚拟线程执行器，每个任务一个虚拟线程，无界但无平台线程占用
  private final ExecutorService toolExecutorPool;
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /** 默认构造器（30 秒超时） */
  public DefaultToolRegistry() {
    this(30);
  }

  /**
   * 构造工具注册中心。
   *
   * @param defaultTimeoutSeconds 工具执行超时（秒），0 表示不限时
   */
  public DefaultToolRegistry(int defaultTimeoutSeconds) {
    this.defaultTimeoutSeconds = defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : 0;
    this.toolExecutorPool = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public void register(String name, ToolExecutor executor) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("工具名称不能为空");
    }
    if (executor == null) {
      throw new IllegalArgumentException("工具执行器不能为 null");
    }
    ToolRegistration registration =
        ToolRegistration.builder()
            .name(name)
            .description("Tool: " + name)
            .executor(executor)
            .build();
    registry.put(name, registration);
    log.info("[Tool-Registry] 注册工具: {}", name);
  }

  /**
   * 注册一个携带完整元数据的工具条目。
   *
   * <p>与 {@link #register(String, ToolExecutor)} 的区别在于：后者只能生成 {@code "Tool: xxx"} 这类占位描述与空参数
   * schema，而本方法保留调用方定义的 description 与参数 schema，LLM 才能据此正确选择工具并填充入参， 因此注解扫描与显式声明场景应优先使用本方法。
   *
   * <p>以工具名为键写入，同名工具后注册者覆盖先注册者，可用于运行时热更新工具实现。
   *
   * <p><b>并发</b>：底层为 {@link ConcurrentHashMap}，可在运行期安全并发调用。
   *
   * @param registration 工具注册条目，不可为 {@code null}，其 name 需全局唯一
   */
  public void register(ToolRegistration registration) {
    registry.put(registration.getName(), registration);
    log.info(
        "[Tool-Registry] 注册工具: {} (desc={})",
        registration.getName(),
        registration.getDefinition().getDescription());
  }

  @Override
  public void unregister(String name) {
    registry.remove(name);
    log.info("[Tool-Registry] 注销工具: {}", name);
  }

  @Override
  public String execute(ToolCall toolCall) {
    ToolRegistration registration = registry.get(toolCall.getName());
    if (registration == null) {
      log.warn("[Tool-Registry] 工具未找到: {}", toolCall.getName());
      return YdszJson.toJson(Map.of("error", "工具未找到: " + toolCall.getName()));
    }
    long startTime = System.currentTimeMillis();
    // 无超时配置时直接同步执行
    if (defaultTimeoutSeconds <= 0) {
      return executeInternal(registration, toolCall, startTime);
    }
    // 有超时配置时通过 Future 异步执行并限时等待
    Future<String> future = toolExecutorPool.submit(() -> executeInternal(registration, toolCall, startTime));
    try {
      return future.get(defaultTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      long duration = System.currentTimeMillis() - startTime;
      log.error("[Tool-Registry] 工具执行超时: {} ({}ms > {}s)", toolCall.getName(), duration, defaultTimeoutSeconds);
      return YdszJson.toJson(
          Map.of("error", "工具执行超时（" + defaultTimeoutSeconds + "s）", "tool", toolCall.getName()));
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("[Tool-Registry] 工具执行失败: {} ({}ms): {}", toolCall.getName(), duration, e.getMessage(), e);
      return YdszJson.toJson(
          Map.of("error", "工具执行失败: " + e.getMessage(), "tool", toolCall.getName()));
    }
  }

  /** 内部执行逻辑（不含超时控制）。 */
  private String executeInternal(ToolRegistration registration, ToolCall toolCall, long startTime) {
    try {
      String result = registration.getExecutor().execute(toolCall.getArguments());
      long duration = System.currentTimeMillis() - startTime;
      log.info("[Tool-Registry] 工具执行完成: {} ({}ms)", toolCall.getName(), duration);
      return result;
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error(
          "[Tool-Registry] 工具执行失败: {} ({}ms): {}", toolCall.getName(), duration, e.getMessage(), e);
      return YdszJson.toJson(
          Map.of("error", "工具执行失败: " + e.getMessage(), "tool", toolCall.getName()));
    }
  }

  @Override
  public List<ToolDefinition> getToolDefinitions() {
    List<ToolDefinition> defs = new ArrayList<>(registry.size());
    for (ToolRegistration reg : registry.values()) {
      defs.add(reg.getDefinition());
    }
    return defs;
  }

  @Override
  public int size() {
    return registry.size();
  }

  @Override
  public boolean contains(String name) {
    return registry.containsKey(name);
  }
}
