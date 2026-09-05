package com.njydsz.generator.engine;

import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.domain.tool.VelocityDateTool;
import com.njydsz.generator.domain.tool.VelocityTextTool;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.vo.CodePreviewVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成引擎（Velocity 模板渲染 + 全局宏）。
 *
 * <p>核心能力：
 * <ul>
 *   <li>加载模板内容 + 构建上下文 → 渲染 Java/Vue 源码</li>
 *   <li>支持全局宏定义（velocity_implicit.vm 自动注入）</li>
 *   <li>支持 {@code $text} / {@code $dateTool} 辅助工具对象</li>
 *   <li>支持列级别覆盖配置（overrideJavaType/overrideFieldName）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Component
public class CodeGenEngine {

  /** Velocity 模板引擎。 */
  private final VelocityEngine velocityEngine;

  /** 默认冲突策略。 */
  private static final ConflictStrategyEnum DEFAULT_CONFLICT = ConflictStrategyEnum.SKIP;

  /**
   * 构造器注入 VelocityEngine。
   *
   * @param velocityEngine Spring 管理的 VelocityEngine
   */
  public CodeGenEngine(VelocityEngine velocityEngine) {
    this.velocityEngine = velocityEngine;
  }

  /**
   * 渲染单个模板生成代码。
   *
   * @param template     模板实体
   * @param contextData  上下文数据映射
   * @return 渲染后的代码字符串
   */
  public String renderTemplate(GenTemplate template, Map<String, Object> contextData) {
    VelocityContext ctx = new VelocityContext(contextData);
    StringWriter writer = new StringWriter(4096);
    String tpl = "#parse(\"velocity_implicit.vm\")\n" + template.getContent();
    velocityEngine.evaluate(ctx, writer, template.getFileName(), tpl);
    return writer.toString();
  }

  /**
   * 构建列信息上下文（注入 table 对象）。
   *
   * @param columns 列元数据列表
   * @return 表格上下文映射
   */
  public Map<String, Object> buildTableContext(List<GenColumnMeta> columns) {
    Map<String, Object> table = new HashMap<>(16);
    table.put("columns", columns);
    table.put("allColumns", columns);
    return table;
  }

  /**
   * 构建完整的生成上下文。
   *
   * @param moduleName    模块名
   * @param basePackage   基础包名
   * @param author        作者
   * @param table         表上下文
   * @param configMap     额外配置
   * @return Velocity 上下文数据
   */
  public Map<String, Object> buildContext(
      String moduleName, String basePackage, String author,
      Map<String, Object> table, Map<String, Object> configMap) {
    Map<String, Object> ctx = new HashMap<>(32);
    ctx.put("module", moduleName);
    ctx.put("package", basePackage);
    ctx.put("author", author);
    ctx.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    ctx.put("table", table);
    // 包路径段
    ctx.put("domainPackage", basePackage + ".domain");
    ctx.put("infraPackage", basePackage + ".infra");
    ctx.put("serverPackage", basePackage + ".server");
    ctx.put("webPackage", basePackage + ".web");
    ctx.put("apiPackage", basePackage + ".api");
    // 全局工具对象
    ctx.put("text", new VelocityTextTool());
    ctx.put("dateTool", new VelocityDateTool());
    // 外部配置
    if (configMap != null) {
      ctx.putAll(configMap);
    }
    return ctx;
  }

  /**
   * 预览代码（不写入文件）。
   *
   * @param template    模板
   * @param contextData 上下文数据
   * @return 预览 VO
   */
  public CodePreviewVO preview(GenTemplate template, Map<String, Object> contextData) {
    String content = renderTemplate(template, contextData);
    return CodePreviewVO.builder()
        .fileName(template.getFileName())
        .filePath(template.getFileName())
        .content(content)
        .conflict(false)
        .build();
  }

  /**
   * 计算文件 MD5 哈希。
   *
   * @param content 文件内容
   * @return MD5 哈希字符串
   */
  public String computeHash(String content) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      log.warn("计算 MD5 失败: {}", e.getMessage());
      return "";
    }
  }
}
