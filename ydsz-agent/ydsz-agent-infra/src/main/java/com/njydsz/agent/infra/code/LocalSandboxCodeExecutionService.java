package com.njydsz.agent.infra.code;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.code.CodeExecutionException;
import com.njydsz.agent.domain.code.CodeExecutionRequest;
import com.njydsz.agent.domain.code.CodeExecutionResult;
import com.njydsz.agent.domain.code.CodeExecutionService;

/**
 * 本地降级模式的代码执行服务实现。
 *
 * <p>通过 ProcessBuilder 调用系统 Python 解释器执行代码。
 * 前置 AST 安全检查，确保只有白名单内的模块被 import，禁用危险内置函数。
 *
 * <p>安全措施：
 *
 * <ul>
 *   <li>模块白名单（AST 静态分析）</li>
 *   <li>危险内置函数检测（eval/exec/open/__import__ 等）</li>
 *   <li>超时控制（Process.waitFor + destroyForcibly）</li>
 *   <li>环境变量 YDSZ_INPUT 传递 JSON 输入数据</li>
 * </ul>
 *
 * <p>注意：本地模式安全性低于 Docker 模式。建议生产环境使用 Docker 沙箱。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.agent.code-execution",
    name = "mode",
    havingValue = "local")
public class LocalSandboxCodeExecutionService implements CodeExecutionService {

  /** 进程退出等待宽限（秒） */
  private static final int PROCESS_EXIT_GRACE_SECONDS = 5;

  /** Python 解释器路径 */
  private final String pythonPath;

  /** 允许的模块白名单 */
  private final List<String> allowedModules;

  /** AST 导入检查器 */
  private final AstImportChecker astChecker;

  /**
   * 构造本地沙箱执行器。
   *
   * @param pythonPath Python 解释器路径
   * @param allowedModules 允许的模块白名单
   */
  public LocalSandboxCodeExecutionService(
      @Value("${ydsz.agent.code-execution.python-path:python3}") String pythonPath,
      @Value("${ydsz.agent.code-execution.allowed-modules:json,math,statistics,itertools,collections,datetime,functools,operator,re,string}")
          List<String> allowedModules) {
    this.pythonPath = pythonPath;
    this.allowedModules = allowedModules;
    this.astChecker = new AstImportChecker(pythonPath);
  }

  @Override
  public CodeExecutionResult execute(CodeExecutionRequest request) throws CodeExecutionException {
    long startTime = System.currentTimeMillis();

    // 1. AST 安全检查
    List<String> violations = astChecker.validate(request.code(), request.allowedModules());
    if (!violations.isEmpty()) {
      long duration = System.currentTimeMillis() - startTime;
      log.warn("[LocalCodeExecution] 安全检查未通过: {}", violations);
      StringBuilder errorMsg = new StringBuilder("安全拦截: ");
      for (String v : violations) {
        if (v.startsWith("IMPORT_BLOCKED:")) {
          String module = v.substring("IMPORT_BLOCKED:".length());
          errorMsg.append("模块 '").append(module).append("' 不在白名单中; ");
        } else if (v.startsWith("DANGEROUS_BUILTIN:")) {
          String builtin = v.substring("DANGEROUS_BUILTIN:".length());
          errorMsg.append("禁用危险内置函数 '").append(builtin).append("'; ");
        } else {
          errorMsg.append(v).append("; ");
        }
      }
      CodeExecutionException ex = CodeExecutionException.compilationError(errorMsg.toString());
      throw ex;
    }

    // 2. 启动进程执行
    Process process = null;
    try {
      ProcessBuilder pb = new ProcessBuilder(pythonPath, "-c", request.code());
      pb.redirectErrorStream(false);

      // 通过环境变量传递输入数据
      if (request.inputJson() != null && !request.inputJson().isBlank()) {
        pb.environment().put("YDSZ_INPUT", request.inputJson());
      } else {
        pb.environment().put("YDSZ_INPUT", "{}");
      }

      process = pb.start();

      // 3. 等待执行完成或超时
      boolean finished = process.waitFor(request.timeoutSeconds(), TimeUnit.SECONDS);
      long duration = System.currentTimeMillis() - startTime;

      if (!finished) {
        process.destroyForcibly();
        log.warn("[LocalCodeExecution] 执行超时: {}s", request.timeoutSeconds());
        throw CodeExecutionException.timeout(request.timeoutSeconds(), duration);
      }

      // 4. 读取输出
      String output = readStream(process.getInputStream());
      String error = readStream(process.getErrorStream());
      int exitCode = process.exitValue();

      if (exitCode != 0) {
        return CodeExecutionResult.failure(error, duration, exitCode);
      }
      return CodeExecutionResult.success(output, duration);

    } catch (CodeExecutionException e) {
      throw e;
    } catch (IOException e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("[LocalCodeExecution] IO 异常: {}", e.getMessage(), e);
      throw new CodeExecutionException(
          "代码执行 IO 异常: " + e.getMessage(),
          CodeExecutionResult.failure(e.getMessage(), duration, -1),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      long duration = System.currentTimeMillis() - startTime;
      if (process != null) {
        process.destroyForcibly();
      }
      throw new CodeExecutionException(
          "代码执行被中断",
          CodeExecutionResult.failure("执行被中断", duration, -1),
          e);
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("[LocalCodeExecution] 未知异常: {}", e.getMessage(), e);
      throw new CodeExecutionException(
          "代码执行异常: " + e.getMessage(),
          CodeExecutionResult.failure(e.getMessage(), duration, -1),
          e);
    }
  }

  @Override
  public boolean isAvailable() {
    try {
      ProcessBuilder pb = new ProcessBuilder(pythonPath, "--version");
      Process process = pb.start();
      boolean finished = process.waitFor(PROCESS_EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (Exception e) {
      log.info("[LocalCodeExecution] Python 不可用: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public List<String> listAllowedModules() {
    return allowedModules;
  }

  /**
   * 读取 InputStream 内容为字符串。
   *
   * @param inputStream 输入流
   * @return 字符串内容
   * @throws IOException IO 异常
   */
  private String readStream(InputStream inputStream) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
    }
    return sb.toString().trim();
  }
}
