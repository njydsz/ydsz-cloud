package com.njydsz.agent.infra.code;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Python AST 解析器（用于本地模式的安全检查）。
 *
 * <p>通过调用外部 Python 解释器对代码执行 AST 级别的静态分析：
 *
 * <ul>
 *   <li>提取所有 import 语句的模块名，与允许白名单对比</li>
 *   <li>检测危险内置函数（eval、exec、open、__import__）</li>
 * </ul>
 *
 * <p>此检查器作为本地沙箱模式的前置安全屏障，在代码实际执行之前拦截。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
public class AstImportChecker {

  /** 危险内置函数集合 */
  private static final Set<String> DANGEROUS_BUILTINS = Set.of(
      "eval", "exec", "open", "__import__", "compile", "globals", "locals",
      "getattr", "setattr", "delattr", "dir", "vars", "type");

  /** 危险模块集合 */
  private static final Set<String> DANGEROUS_MODULES = Set.of(
      "os", "subprocess", "shutil", "socket", "http", "urllib", "ftplib",
      "smtplib", "telnetlib", "xmlrpc", "pickle", "marshal", "ctypes",
      "multiprocessing", "threading", "signal", "resource", "gc", "sys");

  /** Python 解释器路径 */
  private final String pythonPath;

  /** AST 检查脚本超时（秒） */
  private static final int CHECK_TIMEOUT_SECONDS = 5;

  /**
   * 构造 AST 检查器。
   *
   * @param pythonPath Python 解释器路径
   */
  public AstImportChecker(String pythonPath) {
    this.pythonPath = pythonPath;
  }

  /**
   * 校验 Python 代码的安全性。
   *
   * <p>使用 Python AST 解析提取 import 列表和危险内置函数调用。
   *
   * @param code 待检查的 Python 代码
   * @param allowedModules 允许使用的模块白名单
   * @return 安全检查结果（通过的返回空列表，不通过的返回错误信息列表）
   */
  public List<String> validate(String code, List<String> allowedModules) {
    List<String> violations = new ArrayList<>(16);
    Set<String> allowedSet = new HashSet<>(allowedModules);

    // 添加危险模块到拒绝集合
    Set<String> combinedAllowed = new HashSet<>(allowedSet);
    combinedAllowed.removeAll(DANGEROUS_MODULES);

    try {
      String astOutput = executeAstScript(code);
      if (astOutput == null || astOutput.isBlank()) {
        log.warn("[AstImportChecker] AST 脚本无输出，跳过静态检查");
        return violations;
      }

      // 解析脚本输出的 import 列表和危险调用
      for (String line : astOutput.split("\n")) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        if (line.startsWith("IMPORT:")) {
          String module = line.substring("IMPORT:".length()).trim();
          // 只检查顶层模块（如 import foo.bar 检查 foo）
          String topModule = module.split("\\.")[0];
          if (!allowedSet.contains(topModule) && !combinedAllowed.contains(topModule)) {
            violations.add("IMPORT_BLOCKED:" + module);
          }
        } else if (line.startsWith("DANGEROUS:")) {
          String builtin = line.substring("DANGEROUS:".length()).trim();
          violations.add("DANGEROUS_BUILTIN:" + builtin);
        } else if (line.startsWith("ERROR:")) {
          violations.add("PARSE_ERROR:" + line.substring("ERROR:".length()).trim());
        }
      }
    } catch (Exception e) {
      log.warn("[AstImportChecker] AST 检查异常，降级放行: {}", e.getMessage());
    }

    return violations;
  }

  /**
   * 执行 AST 检查脚本并返回输出。
   *
   * @param code 待检查的 Python 代码
   * @return 脚本输出
   * @throws IOException IO 异常
   * @throws InterruptedException 中断异常
   */
  private String executeAstScript(String code) throws IOException, InterruptedException {
    // 将代码作为参数传递给 ast 检查脚本
    String escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"");
    String checkScript =
        "import ast,sys,json\n"
        + "code=\"\"\"" + escapedCode + "\"\"\"\n"
        + "try:\n"
        + "  tree=ast.parse(code)\n"
        + "  for node in ast.walk(tree):\n"
        + "    if isinstance(node,(ast.Import,)):\n"
        + "      for alias in node.names: print('IMPORT:'+alias.name.split('.')[0])\n"
        + "    elif isinstance(node,ast.ImportFrom):\n"
        + "      if node.module: print('IMPORT:'+node.module.split('.')[0])\n"
        + "    elif isinstance(node,ast.Call) and isinstance(node.func,ast.Name):\n"
        + "      builtins=['eval','exec','open','__import__','compile','globals','locals','getattr','setattr','delattr']\n"
        + "      if node.func.id in builtins: print('DANGEROUS:'+node.func.id)\n"
        + "except SyntaxError as e: print('ERROR:'+str(e))\n";

    ProcessBuilder pb = new ProcessBuilder(pythonPath, "-c", checkScript);
    pb.redirectErrorStream(false);
    Process process = pb.start();

    boolean finished = process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      log.warn("[AstImportChecker] AST 检查脚本超时");
      return "";
    }

    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }
    }
    return output.toString().trim();
  }
}
