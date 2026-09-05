package com.njydsz.generator.controller;

import java.util.List;
import java.util.Map;

import com.njydsz.generator.config.GeneratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.service.CodeGeneratorService;

/**
 * 代码生成器 REST 接口
 *
 * <p>提供运行时触发代码生成的能力（无需重启应用）：
 *
 * <ul>
 *   <li>{@code POST /api/v1/generate} — 为指定表生成代码</li>
 *   <li>{@code POST /api/v1/generate/all} — 为全部已配置表生成代码</li>
 *   <li>{@code GET /api/v1/generate/preview} — 预览单表代码（不写文件）</li>
 *   <li>{@code GET /api/v1/generate/templates} — 列出全部可用模板</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generate")
@RequiredArgsConstructor
public class GeneratorController {

  private final CodeGeneratorService generatorService;

  private final GeneratorProperties generatorProperties;

  /**
   * 为指定表生成代码。
   *
   * @param request 生成请求（含表名）
   * @return 生成的文件路径列表
   */
  @PostMapping
  public YdszResponse<List<String>> generate(@RequestBody GenerateRequest request) {
    log.info("触发代码生成: tableName={}", request.getTableName());
    List<String> files = generatorService.generateForTable(request.getTableName());
    return YdszResponse.success(files);
  }

  /**
   * 为全部已配置的表生成代码。
   *
   * @return 生成的文件路径列表
   */
  @PostMapping("/all")
  public YdszResponse<List<String>> generateAll() {
    log.info("触发全量代码生成");
    List<String> files = generatorService.generateAllConfigured();
    return YdszResponse.success(files);
  }

  /**
   * 预览单张表代码（不写文件）。
   *
   * @param tableName 表名（如 {@code ydsz_sys_tenant}）
   * @return 模板名 → 渲染后内容的映射
   */
  @GetMapping("/preview")
  public YdszResponse<Map<String, String>> preview(@RequestParam String tableName) {
    log.info("预览代码生成: tableName={}", tableName);
    Map<String, String> preview = generatorService.previewForTable(tableName);
    return YdszResponse.success(preview);
  }

  /**
   * 列出全部可用模板。
   *
   * @return 模板名称列表
   */
  @GetMapping("/templates")
  public YdszResponse<List<String>> listTemplates() {
    log.info("查询可用模板列表");
    List<String> templates = generatorService.listTemplateNames();
    return YdszResponse.success(templates);
  }

  /**
   * 列出全部已注册的模板分组。
   *
   * @return 模板分组名称列表
   */
  @GetMapping("/template-groups")
  public YdszResponse<List<String>> listTemplateGroups() {
    log.info("查询模板分组列表");
    List<String> groups = generatorProperties.listTemplateGroups();
    return YdszResponse.success(groups);
  }

  /**
   * 查询当前生效的配置摘要。
   *
   * @return 当前配置（不含敏感信息）
   */
  @GetMapping("/config")
  public YdszResponse<Map<String, Object>> currentConfig() {
    log.info("查询当前生成器配置");
    Map<String, Object> config = generatorProperties.toConfigSummary();
    return YdszResponse.success(config);
  }

  /**
   * 代码生成请求。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @lombok.Data
  public static class GenerateRequest {
    /** 目标表名（如 {@code ydsz_sys_tenant}） */
    private String tableName;
  }
}
