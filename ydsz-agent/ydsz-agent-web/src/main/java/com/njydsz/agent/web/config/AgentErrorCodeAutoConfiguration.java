package com.njydsz.agent.web.config;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.config.YdszExceptionCoreAutoConfiguration;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * Agent 模块错误码显式注册配置。
 *
 * <p><b>设计说明：</b>{@link AgentExceptionCode} 已通过 {@code @YdszExceptionCode} 注解标注，
 * 并由 {@link com.njydsz.common.exception.registry.ExceptionCodeScanner} 在启动时自动扫描注册
 * （编译时索引文件 {@code META-INF/spring/ydsz-exception-codes.idx} 已列出全限定名）。
 *
 * <p>本配置作为<b>兜底安全网</b>：在全部单例就绪后（{@code SmartInitializingSingleton} 语义保证），
 * 校验 Agent 错误码是否已被扫描器注册；若因某些边缘原因（如索引文件未打包、扫描器启动失败）未被注册，
 * 则执行显式注册，确保 Agent 错误码始终在 {@link ErrorCodeTable} 中可查。
 *
 * <p>已通过扫描器注册的场景下，本配置为无操作（idempotent），零运行时开销。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(YdszExceptionCoreAutoConfiguration.class)
public class AgentErrorCodeAutoConfiguration implements SmartInitializingSingleton {

  private final ErrorCodeTable errorCodeTable;

  public AgentErrorCodeAutoConfiguration(ErrorCodeTable errorCodeTable) {
    this.errorCodeTable = errorCodeTable;
  }

  /**
   * 在所有单例（含 ExceptionCodeScanner）就绪后，校验并兜底注册 Agent 错误码。
   *
   * <p>执行顺序由 {@link SmartInitializingSingleton} 语义保证：
   * 在全部普通单例（含 ExceptionCodeScanner 的 Bean 创建）完成后触发，
   * 而 {@code ExceptionCodeScanner} 自身也是 {@code SmartInitializingSingleton}，
   * 其 {@code afterSingletonsInstantiated()} 中已调用 {@code scanAndRegister()}。
   * 因此当本方法执行时，扫描器注册流程已完成。
   */
  @Override
  public void afterSingletonsInstantiated() {
    if (isAgentCodeRegistered()) {
      log.debug("[Agent-ErrorCode] AgentExceptionCode 已由 ExceptionCodeScanner 自动注册，跳过显式注册");
      return;
    }
    registerAgentCodes();
  }

  /**
   * 判断 AgentExceptionCode 是否已成功注册（以第一个码 B94001 抽检）。
   *
   * @return true-已注册
   */
  private boolean isAgentCodeRegistered() {
    return errorCodeTable.lookup("B94001") != null;
  }

  /**
   * 显式将 AgentExceptionCode 注册到 ErrorCodeTable。
   *
   * <p>仅在扫描器未运行时执行，避免与扫描器重复注册导致的唯一性冲突。
   * 注：{@link ErrorCodeTable#registerAll(Map)} 内置 fail-fast 唯一性校验（重复时抛
   * {@code IllegalStateException}），本方法调用前已通过 {@link #isAgentCodeRegistered()} 排除该场景。
   */
  private void registerAgentCodes() {
    log.info("[Agent-ErrorCode] ExceptionCodeScanner 未注册 AgentExceptionCode，执行兜底显式注册");
    Map<String, ExceptionCode> codeMap = new HashMap<>(AgentExceptionCode.values().length);
    errorCodeTable.registerModule("agent", "AI Agent");
    for (AgentExceptionCode code : AgentExceptionCode.values()) {
      errorCodeTable.registerCode("agent", code.getCode(), code.getKey(), code.name());
      codeMap.put(code.getCode(), code);
    }
    errorCodeTable.registerAll(codeMap);
    errorCodeTable.validateGlobalAdmission();
    log.info(
        "[Agent-ErrorCode] AgentExceptionCode 显式注册完成，共 {} 个错误码",
        AgentExceptionCode.values().length);
  }
}
