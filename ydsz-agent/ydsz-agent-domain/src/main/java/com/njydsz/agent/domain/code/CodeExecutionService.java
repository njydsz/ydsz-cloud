package com.njydsz.agent.domain.code;

import java.util.List;

/**
 * 代码执行服务接口（领域网关）。
 *
 * <p>在沙箱环境中安全执行 Python 代码块，用于数据分析、统计计算和简单 ML 预测。
 * 实现位于 infra 层，支持 Docker 沙箱和本地降级两种模式。
 *
 * <p>安全约束：
 *
 * <ul>
 *   <li>网络隔离（Docker 模式下 --network=none）
 *   <li>内存 / CPU 限制
 *   <li>模块白名单——仅允许 import 安全模块
 *   <li>超时控制——超时后强制终止
 *   <li>文件系统只读（只挂载 /tmp）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface CodeExecutionService {

  /**
   * 执行 Python 代码块。
   *
   * <p>在隔离的 Python 3 沙箱环境中运行代码，通过环境变量 YDSZ_INPUT 传入 JSON 输入数据。
   * 代码中可通过 {@code os.environ["YDSZ_INPUT"]} 读取输入数据。
   *
   * @param request 执行请求（代码 + 可选输入 + 超时 + 白名单）
   * @return 执行结果（成功时含 stdout，失败时含错误信息）
   * @throws CodeExecutionException 执行超时 / 模块被拒 / 编译错误 / 运行时错误
   */
  CodeExecutionResult execute(CodeExecutionRequest request) throws CodeExecutionException;

  /**
   * 检查代码执行环境是否可用。
   *
   * <p>当 mode=docker 时检查 Docker 守护进程是否可达；
   * 当 mode=local 时检查 Python 解释器是否存在且可执行。
   *
   * @return true=可用，false=不可用（将禁用代码执行功能）
   */
  boolean isAvailable();

  /**
   * 获取允许使用的模块白名单。
   *
   * @return 模块名列表
   */
  List<String> listAllowedModules();
}
