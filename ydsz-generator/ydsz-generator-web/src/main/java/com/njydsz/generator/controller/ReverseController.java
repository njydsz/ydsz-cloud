package com.njydsz.generator.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.service.EntityReverseService;

/**
 * 实体类反向生成 REST 控制器。
 *
 * <p><b>实验性功能（Experimental）：</b>当前处于内部验证阶段，接口签名与响应格式可能在后续版本变动，
 * 不建议外部系统直接调用。稳定后将移除此标注。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@Secured("ROLE_GENERATOR_USER")
@RestController
@RequestMapping("/generator/reverse")
@RequiredArgsConstructor
public class ReverseController {

  private final EntityReverseService reverseService;

  /**
   * 反向分析单个 Java 源文件并生成代码。
   *
   * <p>通过解析 Java 源文件（Entity/POJO 类）中的字段和注解，
   * 结合模板分组生成对应的技术栈代码文件。
   * 属于实验性功能，接口签名可能在后续版本变动。
   *
   * @param sourceFilePath  待分析的 Java 源文件绝对路径（需服务器可访问）
   * @param templateGroupId 模板分组 ID，决定生成的目标技术栈与代码风格
   * @param outputDir       生成文件的输出目录
   * @return 分析报告（JSON 格式），包含生成的文件列表及状态
   */
  @PostMapping("/analyze")
  @Audit(module = "反向生成", action = AuditAction.OTHER, content = "'反向分析Java源文件'", recordRequest = false)
  public YdszResponse<String> analyze(
      @RequestParam String sourceFilePath,
      @RequestParam Long templateGroupId,
      @RequestParam String outputDir) {
    String result = reverseService.reverseGenerate(sourceFilePath, templateGroupId, outputDir);
    return YdszResponse.success(result);
  }

  /**
   * 批量反向分析指定目录下的所有 Java 源文件。
   *
   * <p>递归扫描目录中所有 .java 文件，逐一解析并生成代码。
   * 属于实验性功能，接口签名可能在后续版本变动。
   *
   * @param sourceDirPath   待分析的 Java 源文件目录绝对路径（需服务器可访问）
   * @param templateGroupId 模板分组 ID，决定生成的目标技术栈与代码风格
   * @param outputDir       生成文件的输出目录
   * @return 分析报告列表（JSON 格式），每项对应一个 Java 源文件的分析结果
   */
  @PostMapping("/analyze-batch")
  @Audit(module = "反向生成", action = AuditAction.OTHER, content = "'批量反向分析目录'", recordRequest = false)
  public YdszResponse<List<String>> analyzeBatch(
      @RequestParam String sourceDirPath,
      @RequestParam Long templateGroupId,
      @RequestParam String outputDir) {
    List<String> results = reverseService.reverseBatch(sourceDirPath, templateGroupId, outputDir);
    return YdszResponse.success(results);
  }
}
