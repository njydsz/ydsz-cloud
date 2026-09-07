package com.njydsz.agent.infra.code;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.code.CodeExecutionException;
import com.njydsz.agent.domain.code.CodeExecutionRequest;
import com.njydsz.agent.domain.code.CodeExecutionResult;
import com.njydsz.agent.domain.code.CodeExecutionService;

/**
 * 无操作代码执行服务实现（兜底）。
 *
 * <p>当 Docker 模式和本地模式均未启用时，作为 {@link CodeExecutionService} 的兜底实现，
 * 保证 Spring 容器中始终有该接口的 Bean 可用，避免依赖注入失败。
 *
 * <p>所有执行请求均返回禁用状态结果，{@link #isAvailable()} 始终返回 false。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
public class NoopCodeExecutionService implements CodeExecutionService {

  /** 允许的模块白名单（用于 listAllowedModules 返回） */
  private final List<String> allowedModules;

  /**
   * 构造无操作执行器。
   *
   * @param allowedModules 允许使用的模块白名单
   */
  public NoopCodeExecutionService(List<String> allowedModules) {
    this.allowedModules = allowedModules;
  }

  @Override
  public CodeExecutionResult execute(CodeExecutionRequest request) throws CodeExecutionException {
    log.debug("[NoopCodeExecution] 代码执行功能未启用，拒绝执行");
    throw new CodeExecutionException(
        "代码执行功能未启用（ydsz.agent.code-execution.enabled=false 或非 docker/local 模式）",
        CodeExecutionResult.failure("代码执行功能未启用", 0, -1));
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public List<String> listAllowedModules() {
    return allowedModules;
  }
}
