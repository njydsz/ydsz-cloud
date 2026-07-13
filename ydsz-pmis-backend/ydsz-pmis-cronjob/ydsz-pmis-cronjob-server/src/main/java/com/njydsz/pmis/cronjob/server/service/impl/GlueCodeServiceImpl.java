package com.njydsz.pmis.cronjob.server.service.impl.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.cronjob.domain.entity.schedule.GlueCodeDO;
import com.njydsz.pmis.cronjob.infra.mapper.schedule.GlueCodeMapper;
import com.njydsz.pmis.cronjob.server.service.schedule.GlueCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import groovy.lang.GroovyClassLoader;
import java.util.ArrayList;

/**
 * GLUE 在线编码服务实现（P1-2 GLUE 在线编码）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@code save}: 查询当前最大版本号，version+1 后插入新记录</li>
 *   <li>{@code getLatest}: 透传 mapper.selectLatestByJobId</li>
 *   <li>{@code listVersions}: LambdaQueryWrapper 按 version 降序查询</li>
 *   <li>{@code rollback}: 查询目标版本代码，创建新版本（version=max+1）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlueCodeServiceImpl implements GlueCodeService {

    /** GLUE 代码 Mapper（版本化源码 CRUD） */
    private final GlueCodeMapper glueCodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlueCodeDO save(String jobId, String sourceCode, String language, String remark) {
        if (!StringUtils.hasText(jobId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_glue_job_id_required");
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_glue_source_required");
        }
        // 计算新版本号
        GlueCodeDO latest = glueCodeMapper.selectLatestByJobId(jobId);
        int nextVersion = latest == null ? 1 : (latest.getVersion() == null ? 1 : latest.getVersion() + 1);

        GlueCodeDO entity = new GlueCodeDO();
        entity.setJobId(jobId);
        entity.setSourceCode(sourceCode);
        entity.setLanguage(StringUtils.hasText(language) ? language : "GROOVY");
        entity.setVersion(nextVersion);
        entity.setRemark(remark);
        glueCodeMapper.insert(entity);
        log.info("[Glue] 保存 GLUE 代码: jobId={} version={} remark={}", jobId, nextVersion, remark);
        return entity;
    }

    @Override
    public GlueCodeDO getLatest(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return null;
        }
        return glueCodeMapper.selectLatestByJobId(jobId);
    }

    @Override
    public List<GlueCodeDO> listVersions(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<GlueCodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlueCodeDO::getJobId, jobId)
                .orderByDesc(GlueCodeDO::getVersion);
        return glueCodeMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlueCodeDO rollback(String jobId, Integer version) {
        if (!StringUtils.hasText(jobId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_glue_job_id_required");
        }
        if (version == null || version < 1) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_glue_version_invalid");
        }
        // 查询目标版本
        LambdaQueryWrapper<GlueCodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlueCodeDO::getJobId, jobId)
                .eq(GlueCodeDO::getVersion, version);
        GlueCodeDO target = glueCodeMapper.selectOne(wrapper);
        if (target == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.cronjob.msg_glue_version_not_found");
        }
        // 创建新版本（内容为目标版本代码）
        GlueCodeDO latest = glueCodeMapper.selectLatestByJobId(jobId);
        int nextVersion = latest == null ? 1 : (latest.getVersion() == null ? 1 : latest.getVersion() + 1);

        GlueCodeDO entity = new GlueCodeDO();
        entity.setJobId(jobId);
        entity.setSourceCode(target.getSourceCode());
        entity.setLanguage(target.getLanguage());
        entity.setVersion(nextVersion);
        entity.setRemark("rollback to v" + version);
        glueCodeMapper.insert(entity);
        log.info("[Glue] 回滚 GLUE 代码: jobId={} fromVersion={} toNewVersion={}", jobId, version, nextVersion);
        return entity;
    }

    // ==================== P1-1: 在线测试 / 模板 / 差异对比 ====================

    /** 测试执行超时时间（毫秒） */
    private static final long TEST_TIMEOUT_MS = 10_000;

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

    @Override
    public Map<String, String> getCodeTemplate(String language) {
        String lang = StringUtils.hasText(language) ? language.toUpperCase() : "GROOVY";
        Map<String, String> template = new HashMap<>();
        template.put("language", lang);
        switch (lang) {
            case "GROOVY", "JAVA" -> {
                template.put("template",
                        "// GLUE Groovy 模板\n" +
                        "// 实现 JobHandler 接口或定义 execute 方法\n" +
                        "import com.njydsz.pmis.common.core.job.JobHandler\n" +
                        "import com.njydsz.pmis.common.core.job.ProcessResult\n" +
                        "\n" +
                        "class MyJob implements JobHandler {\n" +
                        "    @Override\n" +
                        "    ProcessResult execute(String paramsJson) {\n" +
                        "        // TODO: 编写业务逻辑\n" +
                        "        println(\"params: \" + paramsJson)\n" +
                        "        return ProcessResult.success()\n" +
                        "    }\n" +
                        "}");
                template.put("description", "Groovy 脚本模板，实现 JobHandler 接口");
            }
            case "PYTHON" -> {
                template.put("template",
                        "#!/usr/bin/env python3\n" +
                        "# GLUE Python 模板\n" +
                        "import os\n" +
                        "import json\n" +
                        "\n" +
                        "params = os.environ.get('JOB_PARAMS', '{}')\n" +
                        "print(f'params: {params}')\n" +
                        "\n" +
                        "# TODO: 编写业务逻辑\n" +
                        "print('Hello from Python!')\n");
                template.put("description", "Python3 脚本模板，通过环境变量传入参数");
            }
            case "SHELL" -> {
                template.put("template",
                        "#!/bin/bash\n" +
                        "# GLUE Shell 模板\n" +
                        "echo \"params: $JOB_PARAMS\"\n" +
                        "\n" +
                        "# TODO: 编写业务逻辑\n" +
                        "echo \"Hello from Shell!\"\n");
                template.put("description", "Bash 脚本模板，通过环境变量传入参数");
            }
            case "JAVASCRIPT" -> {
                template.put("template",
                        "// GLUE JavaScript 模板\n" +
                        "// paramsJson 全局变量包含任务参数\n" +
                        "function execute(paramsJson) {\n" +
                        "    var params = JSON.parse(paramsJson || '{}');\n" +
                        "    // TODO: 编写业务逻辑\n" +
                        "    return JSON.stringify({success: true, msg: 'Hello from JS!'});\n" +
                        "}\n" +
                        "execute(paramsJson);\n");
                template.put("description", "JavaScript 脚本模板，定义 execute 函数");
            }
            default -> {
                template.put("template", "// Unsupported language: " + lang);
                template.put("description", "不支持的语言");
            }
        }
        return template;
    }

    @Override
    public Map<String, Object> diffVersions(String jobId, Integer versionA, Integer versionB) {
        Map<String, Object> result = new HashMap<>();
        if (!StringUtils.hasText(jobId) || versionA == null || versionB == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_glue_diff_params_required");
        }
        // 查询两个版本
        GlueCodeDO codeA = getVersion(jobId, versionA);
        GlueCodeDO codeB = getVersion(jobId, versionB);
        if (codeA == null || codeB == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.cronjob.msg_glue_version_not_found");
        }
        result.put("versionA", Map.of(
                "version", versionA,
                "sourceCode", codeA.getSourceCode(),
                "remark", codeA.getRemark() != null ? codeA.getRemark() : "",
                "createdAt", codeA.getCreatedAt() != null ? codeA.getCreatedAt().toString() : ""
        ));
        result.put("versionB", Map.of(
                "version", versionB,
                "sourceCode", codeB.getSourceCode(),
                "remark", codeB.getRemark() != null ? codeB.getRemark() : "",
                "createdAt", codeB.getCreatedAt() != null ? codeB.getCreatedAt().toString() : ""
        ));
        // 计算行级差异
        result.put("diff", computeLineDiff(codeA.getSourceCode(), codeB.getSourceCode()));
        return result;
    }

    /**
     * 根据语言执行代码（内存编译，不持久化）。
     */
    private Object executeByLanguage(String sourceCode, String language, String paramsJson) throws Exception {
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
                return language + " code test is not supported in memory, please save and execute via job dispatch";
            }
            default -> {
                return "Unsupported language: " + language;
            }
        }
    }

    /**
     * 查询指定版本。
     */
    private GlueCodeDO getVersion(String jobId, Integer version) {
        LambdaQueryWrapper<GlueCodeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlueCodeDO::getJobId, jobId)
                .eq(GlueCodeDO::getVersion, version);
        return glueCodeMapper.selectOne(wrapper);
    }

    /**
     * 计算行级差异（简单实现）。
     */
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
