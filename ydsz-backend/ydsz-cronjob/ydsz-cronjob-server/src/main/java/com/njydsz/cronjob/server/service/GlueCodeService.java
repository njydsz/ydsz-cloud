package com.njydsz.cronjob.server.service.schedule;

import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.entity.schedule.GlueCode;

/**
 * GLUE 在线编码服务（P1-2 GLUE 在线编码）。
 *
 * <p>提供 GLUE 代码的版本管理能力：保存新版本、查询最新版本、查询版本列表、
 * 按版本回滚。回滚操作本身会创建一个新版本（内容为目标版本代码），
 * 保留完整版本历史便于审计与再次回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface GlueCodeService {

    /**
     * 保存新版本 GLUE 代码。
     *
     * <p>版本号自动递增（max(version)+1），首次保存版本号为 1。
     *
     * @param jobId      任务 ID
     * @param sourceCode 源代码内容
     * @param language   语言（GROOVY / JAVA），为空时默认 GROOVY
     * @param remark     版本备注（可空）
     * @return 新创建的 GLUE 代码版本
     */
    GlueCode save(String jobId, String sourceCode, String language, String remark);

    /**
     * 获取指定任务的最新版本 GLUE 代码。
     *
     * @param jobId 任务 ID
     * @return 最新版本 GLUE 代码；不存在时返回 null
     */
    GlueCode getLatest(String jobId);

    /**
     * 获取指定任务的全部版本列表（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 版本列表；无记录时返回空列表
     */
    List<GlueCode> listVersions(String jobId);

    /**
     * 回滚到指定版本。
     *
     * <p>创建一个新版本（version=max+1），内容为目标版本的源代码，
     * 保留原版本历史不修改。
     *
     * @param jobId   任务 ID
     * @param version 目标版本号
     * @return 新创建的回滚版本
     */
    GlueCode rollback(String jobId, Integer version);

    /**
     * P1-1: 在线测试 GLUE 代码（不保存版本，直接执行）。
     *
     * <p>在内存中编译执行代码并返回结果，不持久化任何数据。
     * 支持超时控制（默认 10s）和异常捕获。
     *
     * @param sourceCode 源代码
     * @param language   语言（GROOVY / PYTHON / SHELL / JAVASCRIPT）
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
     * @param jobId    任务 ID
     * @param versionA 版本 A
     * @param versionB 版本 B
     * @return 差异信息映射
     */
    Map<String, Object> diffVersions(String jobId, Integer versionA, Integer versionB);
}
