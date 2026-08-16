package com.njydsz.cronjob.server.service.impl.schedule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.entity.schedule.GlueCode;
import com.njydsz.cronjob.infra.mapper.schedule.GlueCodeMapper;
import com.njydsz.cronjob.server.service.schedule.GlueCodeService;

import groovy.lang.GroovyClassLoader;

/**
 * GLUE 脚本服务实现。
 *
 * <p>维护任务运行时的 GLUE 脚本（Java/Shell/Python/JS）源码，对应 {@code ydsz_glue_code} 表。
 *
 * <p>支持脚本版本管理、在线编辑、灰度发布、SourceCode 编译为 Class 缓存到本地磁盘，
 *
 * <p>对标 XXL-JOB 的 GLUE 模式，支持 Source / Online 两种加载方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlueCodeServiceImpl implements GlueCodeService {

  /** GLUE 代码 Mapper（版本化源码 CRUD） */
  private final GlueCodeMapper glueCodeMapper;

  /**
   * {@inheritDoc}
   *
   * <p>版本号自增策略：查询当前最大版本号，+1 后插入新记录。语言默认 GROOVY。
   *
   * @param jobId 任务 ID
   * @param sourceCode 源代码内容
   * @param language 编程语言（GROOVY/PYTHON/SHELL/JAVASCRIPT），为空时默认 GROOVY
   * @param remark 版本备注
   * @return 新创建的 GLUE 代码版本记录
   * @throws SysException 当 jobId 为空或 sourceCode 为空白时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public GlueCode save(String jobId, String sourceCode, String language, String remark) {
    if (!StringUtils.hasText(jobId)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_glue_job_id_required")
          .build();
    }
    if (sourceCode == null || sourceCode.isBlank()) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_glue_source_required")
          .build();
    }
    // 计算新版本号
    GlueCode latest = glueCodeMapper.selectLatestByJobId(jobId);
    int nextVersion =
        latest == null ? 1 : (latest.getVersion() == null ? 1 : latest.getVersion() + 1);

    GlueCode entity = new GlueCode();
    entity.setJobId(jobId);
    entity.setSourceCode(sourceCode);
    entity.setLanguage(StringUtils.hasText(language) ? language : "GROOVY");
    entity.setVersion(nextVersion);
    entity.setRemark(remark);
    glueCodeMapper.insert(entity);
    log.info("[Glue] 保存 GLUE 代码: jobId={} version={} remark={}", jobId, nextVersion, remark);
    return entity;
  }

  /**
   * {@inheritDoc}
   *
   * @param jobId 任务 ID
   * @return 最新版本的 GLUE 代码记录，jobId 为空或不存在时返回 null
   */
  @Override
  public GlueCode getLatest(String jobId) {
    if (!StringUtils.hasText(jobId)) {
      return null;
    }
    return glueCodeMapper.selectLatestByJobId(jobId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>按版本号降序排列返回全部历史版本。
   *
   * @param jobId 任务 ID
   * @return 版本列表（降序），jobId 为空时返回空列表
   */
  @Override
  public List<GlueCode> listVersions(String jobId) {
    if (!StringUtils.hasText(jobId)) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<GlueCode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GlueCode::getJobId, jobId).orderByDesc(GlueCode::getVersion);
    return glueCodeMapper.selectList(wrapper);
  }

  /**
   * {@inheritDoc}
   *
   * <p>回滚策略：查询目标版本源代码，创建新版本（version=max+1）写入， 而非物理删除中间版本，保证版本链完整性。
   *
   * @param jobId 任务 ID
   * @param version 要回滚到的目标版本号
   * @return 回滚后生成的新版本记录
   * @throws SysException 当 jobId 为空、版本号无效或目标版本不存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public GlueCode rollback(String jobId, Integer version) {
    if (!StringUtils.hasText(jobId)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_glue_job_id_required")
          .build();
    }
    if (version == null || version < 1) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_glue_version_invalid")
          .build();
    }
    // 查询目标版本
    LambdaQueryWrapper<GlueCode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GlueCode::getJobId, jobId).eq(GlueCode::getVersion, version);
    GlueCode target = glueCodeMapper.selectOne(wrapper);
    if (target == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("error.cronjob.msg_glue_version_not_found")
          .build();
    }
    // 创建新版本（内容为目标版本代码）
    GlueCode latest = glueCodeMapper.selectLatestByJobId(jobId);
    int nextVersion =
        latest == null ? 1 : (latest.getVersion() == null ? 1 : latest.getVersion() + 1);

    GlueCode entity = new GlueCode();
    entity.setJobId(jobId);
    entity.setSourceCode(target.getSourceCode());
    entity.setLanguage(target.getLanguage());
    entity.setVersion(nextVersion);
    entity.setRemark("rollback to v" + version);
    glueCodeMapper.insert(entity);
    log.info(
        "[Glue] 回滚 GLUE 代码: jobId={} fromVersion={} toNewVersion={}", jobId, version, nextVersion);
    return entity;
  }

  // ==================== P1-1: 在线测试 / 模板 / 差异对比 ====================

  /** 测试执行超时时间（毫秒） */
  private static final long TEST_TIMEOUT_MS = 10_000;

  /**
   * {@inheritDoc}
   *
   * <p>仅支持 Groovy/Java 内存编译执行（GroovyClassLoader）， Python/Shell/JavaScript 需保存后通过任务调度执行。 执行结果包含
   * success/result/durationMs/error 四个字段。
   *
   * @param sourceCode 源代码内容
   * @param language 编程语言
   * @param paramsJson 任务参数 JSON 字符串
   * @return 执行结果 Map，包含 success、result/error、durationMs
   */
  @Override
  public Map<String, Object> testCode(String sourceCode, String language, String paramsJson) {
    Map<String, Object> result = new HashMap<>();
    if (sourceCode == null || sourceCode.isBlank()) {
      result.put("success", false);
      result.put("error", "Source code is empty");
      return result;
    }
    String lang = StringUtils.hasText(language) ? language.toUpperCase() : "GROOVY";
    long startTime = System.currentTimeMillis();
    try {
      // 根据语言选择执行方式
      Object execResult = executeByLanguage(sourceCode, lang, paramsJson);
      result.put("success", true);
      result.put("result", execResult);
      result.put("durationMs", System.currentTimeMillis() - startTime);
    } catch (Exception e) {
      result.put("success", false);
      result.put("error", e.getMessage());
      result.put("durationMs", System.currentTimeMillis() - startTime);
      log.warn("[Glue] 测试执行失败: lang={} reason={}", lang, e.getMessage());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>支持 GROOVY/JAVA、PYTHON、SHELL、JAVASCRIPT 四种语言模板， 返回 Map 包含 language、template、description 三个字段。
   *
   * @param language 编程语言，为空时默认 GROOVY
   * @return 代码模板 Map
   */
  @Override
  public Map<String, String> getCodeTemplate(String language) {
    String lang = StringUtils.hasText(language) ? language.toUpperCase() : "GROOVY";
    Map<String, String> template = new HashMap<>();
    template.put("language", lang);
    switch (lang) {
      case "GROOVY", "JAVA" -> {
        template.put(
            "template",
            "// GLUE Groovy 模板\n"
                + "// 实现 JobHandler 接口或定义 execute 方法\n"
                + "import com.njydsz.cronjob.domain.job.JobHandler\n"
                + "import com.njydsz.cronjob.domain.job.ProcessResult\n"
                + "\n"
                + "class MyJob implements JobHandler {\n"
                + "    @Override\n"
                + "    ProcessResult execute(String paramsJson) {\n"
                + "        // TODO: 编写业务逻辑\n"
                + "        println(\"params: \" + paramsJson)\n"
                + "        return ProcessResult.success()\n"
                + "    }\n"
                + "}");
        template.put("description", "Groovy 脚本模板，实现 JobHandler 接口");
      }
      case "PYTHON" -> {
        template.put(
            "template",
            "#!/usr/bin/env python3\n"
                + "# GLUE Python 模板\n"
                + "import os\n"
                + "import json\n"
                + "\n"
                + "params = os.environ.get('JOB_PARAMS', '{}')\n"
                + "print(f'params: {params}')\n"
                + "\n"
                + "# TODO: 编写业务逻辑\n"
                + "print('Hello from Python!')\n");
        template.put("description", "Python3 脚本模板，通过环境变量传入参数");
      }
      case "SHELL" -> {
        template.put(
            "template",
            "#!/bin/bash\n"
                + "# GLUE Shell 模板\n"
                + "echo \"params: $JOB_PARAMS\"\n"
                + "\n"
                + "# TODO: 编写业务逻辑\n"
                + "echo \"Hello from Shell!\"\n");
        template.put("description", "Bash 脚本模板，通过环境变量传入参数");
      }
      case "JAVASCRIPT" -> {
        template.put(
            "template",
            "// GLUE JavaScript 模板\n"
                + "// paramsJson 全局变量包含任务参数\n"
                + "function execute(paramsJson) {\n"
                + "    var params = JSON.parse(paramsJson || '{}');\n"
                + "    // TODO: 编写业务逻辑\n"
                + "    return JSON.stringify({success: true, msg: 'Hello from JS!'});\n"
                + "}\n"
                + "execute(paramsJson);\n");
        template.put("description", "JavaScript 脚本模板，定义 execute 函数");
      }
      default -> {
        template.put("template", "// Unsupported language: " + lang);
        template.put("description", "不支持的语言");
      }
    }
    return template;
  }

  /**
   * {@inheritDoc}
   *
   * <p>返回 versionA 和 versionB 的源代码信息及行级差异列表， 差异类型包括 ADDED（新增行）、REMOVED（删除行）、MODIFIED（修改行）。
   *
   * @param jobId 任务 ID
   * @param versionA 对比版本 A
   * @param versionB 对比版本 B
   * @return 差异对比结果，包含 versionA、versionB、diff 三个字段
   * @throws SysException 当参数缺失或版本不存在时抛出
   */
  @Override
  public Map<String, Object> diffVersions(String jobId, Integer versionA, Integer versionB) {
    Map<String, Object> result = new HashMap<>();
    if (!StringUtils.hasText(jobId) || versionA == null || versionB == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_glue_diff_params_required")
          .build();
    }
    // 查询两个版本
    GlueCode codeA = getVersion(jobId, versionA);
    GlueCode codeB = getVersion(jobId, versionB);
    if (codeA == null || codeB == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("error.cronjob.msg_glue_version_not_found")
          .build();
    }
    result.put(
        "versionA",
        Map.of(
            "version",
            versionA,
            "sourceCode",
            codeA.getSourceCode(),
            "remark",
            codeA.getRemark() != null ? codeA.getRemark() : "",
            "createdAt",
            codeA.getCreatedAt() != null ? codeA.getCreatedAt().toString() : ""));
    result.put(
        "versionB",
        Map.of(
            "version",
            versionB,
            "sourceCode",
            codeB.getSourceCode(),
            "remark",
            codeB.getRemark() != null ? codeB.getRemark() : "",
            "createdAt",
            codeB.getCreatedAt() != null ? codeB.getCreatedAt().toString() : ""));
    // 计算行级差异
    result.put("diff", computeLineDiff(codeA.getSourceCode(), codeB.getSourceCode()));
    return result;
  }

  /** 根据语言执行代码（内存编译，不持久化）。 */
  private Object executeByLanguage(String sourceCode, String language, String paramsJson)
      throws Exception {
    // 委托给 GlueJobHandler 的编译执行逻辑
    // 这里简化实现：Groovy 通过 GroovyClassLoader 执行，其他语言返回提示
    switch (language) {
      case "GROOVY", "JAVA" -> {
        try (GroovyClassLoader classLoader = new GroovyClassLoader()) {
          Class<?> clazz = classLoader.parseClass(sourceCode);
          Object instance = clazz.getDeclaredConstructor().newInstance();
          // 尝试调用 execute(String) 方法
          try {
            Method executeMethod = clazz.getMethod("execute", String.class);
            return executeMethod.invoke(instance, paramsJson != null ? paramsJson : "{}");
          } catch (NoSuchMethodException e) {
            // 尝试无参 execute() 方法
            try {
              Method executeMethod = clazz.getMethod("execute");
              return executeMethod.invoke(instance);
            } catch (NoSuchMethodException e2) {
              return "No execute method found";
            }
          }
        }
      }
      case "PYTHON", "SHELL", "JAVASCRIPT" -> {
        return language
            + " code test is not supported in memory, please save and execute via job dispatch";
      }
      default -> {
        return "Unsupported language: " + language;
      }
    }
  }

  /** 查询指定版本。 */
  private GlueCode getVersion(String jobId, Integer version) {
    LambdaQueryWrapper<GlueCode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GlueCode::getJobId, jobId).eq(GlueCode::getVersion, version);
    return glueCodeMapper.selectOne(wrapper);
  }

  /** 计算行级差异（简单实现）。 */
  private List<Map<String, Object>> computeLineDiff(String codeA, String codeB) {
    String[] linesA = codeA != null ? codeA.split("\n") : new String[0];
    String[] linesB = codeB != null ? codeB.split("\n") : new String[0];
    List<Map<String, Object>> diffs = new ArrayList<>();
    int maxLines = Math.max(linesA.length, linesB.length);
    for (int i = 0; i < maxLines; i++) {
      String lineA = i < linesA.length ? linesA[i] : "";
      String lineB = i < linesB.length ? linesB[i] : "";
      if (!lineA.equals(lineB)) {
        Map<String, Object> diff = new HashMap<>();
        diff.put("line", i + 1);
        diff.put("type", lineA.isEmpty() ? "ADDED" : (lineB.isEmpty() ? "REMOVED" : "MODIFIED"));
        diff.put("old", lineA);
        diff.put("new", lineB);
        diffs.add(diff);
      }
    }
    return diffs;
  }
}
