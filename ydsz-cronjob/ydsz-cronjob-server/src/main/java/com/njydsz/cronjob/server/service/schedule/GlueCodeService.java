package com.njydsz.cronjob.server.service.schedule;

import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.vo.GlueCodeVO;

/**
 * GLUE 在线编码 Service
 *
 * <p>提供任务"在线写代码"能力——用户可在任务配置页面编写 GROOVY/PYTHON/SHELL/JAVASCRIPT 脚本
 * 作为任务执行体,无需预编译发布。完整能力包括版本管理、在线测试、模板生成、版本对比与回滚。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本管理</b>：{@link #save} / {@link #listVersions} / {@link #getLatest} — 自动递增版本号
 *   <li><b>回滚</b>：{@link #rollback} — 创建新版本(内容=目标版本)而非物理回写,保留完整历史
 *   <li><b>在线测试</b>：{@link #testCode} — 内存中编译执行,带超时控制和异常捕获
 *   <li><b>代码模板</b>：{@link #getCodeTemplate} — 提供各语言的基础模板
 *   <li><b>版本对比</b>：{@link #diffVersions} — 返回两版本源代码和行级差异
 * </ul>
 *
 * <p><b>安全约束：</b>{@link #testCode} 通过 {@link com.njydsz.cronjob.server.glue.GlueExecutor}
 * 在沙箱中执行,禁止访问文件系统/网络/危险类,超时强制中断(默认 10s)。
 *
 * <p><b>事务：</b>{@link #save} / {@link #rollback} 开启 {@code @Transactional(rollbackFor =
 * Exception.class)}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.cronjob.domain.vo.GlueCodeVO GLUE 代码视图对象
 * @see JobService 任务主 Service(创建 GLUE 任务时调用 save)
 */
public interface GlueCodeService {

  /**
   * 保存新版本 GLUE 代码。
   *
   * <p>版本号自动递增（max(version)+1），首次保存版本号为 1。
   *
   * @param jobId 任务 ID
   * @param sourceCode 源代码内容
   * @param language 语言（GROOVY / JAVA），为空时默认 GROOVY
   * @param remark 版本备注（可空）
   * @return 新创建的 GLUE 代码版本视图对象
   */
  GlueCodeVO save(String jobId, String sourceCode, String language, String remark);

  /**
   * 获取指定任务的最新版本 GLUE 代码。
   *
   * @param jobId 任务 ID
   * @return 最新版本 GLUE 代码视图对象；不存在时返回 null
   */
  GlueCodeVO getLatest(String jobId);

  /**
   * 获取指定任务的全部版本列表（按版本号降序）。
   *
   * @param jobId 任务 ID
   * @return 版本视图对象列表；无记录时返回空列表
   */
  List<GlueCodeVO> listVersions(String jobId);

  /**
   * 回滚到指定版本。
   *
   * <p>创建一个新版本（version=max+1），内容为目标版本的源代码， 保留原版本历史不修改。
   *
   * @param jobId 任务 ID
   * @param version 目标版本号
   * @return 新创建的回滚版本视图对象
   */
  GlueCodeVO rollback(String jobId, Integer version);

  /**
   * P1-1: 在线测试 GLUE 代码（不保存版本，直接执行）。
   *
   * <p>在内存中编译执行代码并返回结果，不持久化任何数据。 支持超时控制（默认 10s）和异常捕获。
   *
   * @param sourceCode 源代码
   * @param language 语言（GROOVY / PYTHON / SHELL / JAVASCRIPT）
   * @param paramsJson 测试参数（JSON 字符串，可空）
   * @return 执行结果，包含 success / result / error / durationMs
   */
  Map<String, Object> testCode(String sourceCode, String language, String paramsJson);

  /**
   * P1-1: 获取代码模板。
   *
   * <p>返回指定语言的代码模板，包含基本结构和示例代码。
   *
   * @param language 语言（GROOVY / PYTHON / SHELL / JAVASCRIPT）
   * @return 模板映射，包含 template / description / language
   */
  Map<String, String> getCodeTemplate(String language);

  /**
   * P1-1: 对比两个版本的差异。
   *
   * <p>返回版本间的差异信息，包含各版本的源代码和行级差异。
   *
   * @param jobId 任务 ID
   * @param versionA 版本 A
   * @param versionB 版本 B
   * @return 差异信息映射
   */
  Map<String, Object> diffVersions(String jobId, Integer versionA, Integer versionB);
}
