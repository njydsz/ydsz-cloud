package com.njydsz.generator.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
