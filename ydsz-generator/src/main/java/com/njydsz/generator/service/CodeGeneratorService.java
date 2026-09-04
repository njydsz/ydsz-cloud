package com.njydsz.generator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.stereotype.Service;

import com.njydsz.generator.config.GeneratorProperties;
import com.njydsz.generator.config.GeneratorProperties.ModuleGroupConfig;
import com.njydsz.generator.model.TableMetadata;

/**
 * 代码生成器核心服务 — 基于数据库表元数据 → 模板渲染 → 文件输出。
 *
 * <p>使用 {@link VelocityEngine} 渲染预置模板，输出 DDD 分层代码：
 *
 * <ul>
 *   <li>domain/entity/{EntityName}.java</li>
 *   <li>domain/dto/{EntityName}DTO.java</li>
 *   <li>domain/query/{EntityName}PageQuery.java</li>
 *   <li>domain/vo/{EntityName}VO.java</li>
 *   <li>domain/repository/{EntityName}Repository.java</li>
 *   <li>infra/mapper/{EntityName}Mapper.java</li>
 *   <li>infra/converter/{ModuleName}Converter.java</li>
 *   <li>infra/repository/{EntityName}RepositoryImpl.java</li>
 *   <li>server/service/{EntityName}Service.java</li>
 *   <li>server/service/impl/{EntityName}ServiceImpl.java</li>
 *   <li>web/controller/{EntityName}Controller.java</li>
 *   <li>api/{EntityName}FeignClient.java</li>
 *   <li>api/assembler/{EntityName}Assembler.java</li>
 * </ul>
 *
 * <p>支持分组配置（{@link GeneratorProperties#getActiveGroup()}）和文件冲突策略
 * （skip / override / merge / prompt）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGeneratorService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String TEMPLATE_ENCODING = "UTF-8";

  private final GeneratorProperties properties;
  private final TableMetadataReader metadataReader;
  private final VelocityEngine velocityEngine;

  /**
   * 为单张表生成全部代码文件。
   *
   * @param tableName - 表名
   * @return 生成的文件路径列表
   */
  public List<String> generateForTable(String tableName) {
    TableMetadata metadata = metadataReader.readTable(tableName);
    return generateAll(metadata);
  }

  /**
   * 为全部配置的表生成代码。
   *
   * @return 生成的文件路径列表
   */
  public List<String> generateAllConfigured() {
    ModuleGroupConfig config = properties.resolveEffectiveConfig();
    List<TableMetadata> tables = readTablesForConfig(config);
    List<String> generated = new ArrayList<>(tables.size() * 8);
    for (TableMetadata table : tables) {
      generated.addAll(generateAll(table));
    }
    return generated;
  }

  /**
   * 预览单张表代码（不写文件）。
   *
   * @param tableName - 表名
   * @return 模板名 → 渲染后内容的映射
   */
  public Map<String, String> previewForTable(String tableName) {
    TableMetadata metadata = metadataReader.readTable(tableName);
    return renderAll(metadata);
  }

  /**
   * 列出全部可用的模板名称。
   *
   * @return 模板名称列表
   */
  public List<String> listTemplateNames() {
    return List.of(
        "entity.vm", "dto.vm", "vo.vm", "query.vm",
        "repository.vm", "repositoryImpl.vm",
        "mapper.vm", "converter.vm",
        "service.vm", "serviceImpl.vm",
        "controller.vm",
        "feign.vm", "assembler.vm", "fallbackFactory.vm"
    );
  }

  // -----------------------------------------------------------------------
  // 私有方法
  // -----------------------------------------------------------------------

  private List<TableMetadata> readTablesForConfig(ModuleGroupConfig config) {
    List<TableMetadata> list = new ArrayList<>(config.getTableNames().size());
    for (String tableName : config.getTableNames()) {
      list.add(metadataReader.readTable(tableName));
    }
    return list;
  }

  /**
   * 执行全量代码生成。
   *
   * @param table - 表元数据
   * @return 生成的文件路径列表
   */
  private List<String> generateAll(TableMetadata table) {
    ModuleGroupConfig config = properties.resolveEffectiveConfig();
    List<String> generated = new ArrayList<>(14);
    Map<String, String> rendered = renderAll(table);
    String entity = table.getEntityName();
    String moduleName = config.getModuleName();

    // 1. domain/entity
    if (config.isGenerateEntity()) {
      generated.add(writeFile("entity.vm", resolvePath(moduleName, "domain/entity", entity + ".java"), rendered));
    }
    // 2. domain/dto + vo + query
    if (config.isGenerateModel()) {
      generated.add(writeFile("dto.vm", resolvePath(moduleName, "domain/dto", entity + "DTO.java"), rendered));
      generated.add(writeFile("vo.vm", resolvePath(moduleName, "domain/vo", entity + "VO.java"), rendered));
      generated.add(writeFile("query.vm", resolvePath(moduleName, "domain/query", entity + "PageQuery.java"), rendered));
    }
    // 3. domain/repository
    if (config.isGenerateRepository()) {
      generated.add(writeFile("repository.vm", resolvePath(moduleName, "domain/repository", entity + "Repository.java"), rendered));
    }
    // 4. infra/mapper
    if (config.isGenerateMapper()) {
      generated.add(writeFile("mapper.vm", resolvePath(moduleName, "infra/mapper", entity + "Mapper.java"), rendered));
    }
    // 5. infra/converter
    if (config.isGenerateConverter()) {
      generated.add(writeFile("converter.vm", resolvePath(moduleName, "infra/converter", capitalize(moduleName) + "Converter.java"), rendered));
    }
    // 6. infra/repositoryImpl
    if (config.isGenerateRepository()) {
      generated.add(writeFile("repositoryImpl.vm", resolvePath(moduleName, "infra/repository", entity + "RepositoryImpl.java"), rendered));
    }
    // 7. server/service
    if (config.isGenerateService()) {
      generated.add(writeFile("service.vm", resolvePath(moduleName, "server/service", entity + "Service.java"), rendered));
      generated.add(writeFile("serviceImpl.vm", resolvePath(moduleName, "server/service/impl", entity + "ServiceImpl.java"), rendered));
    }
    // 8. web/controller
    if (config.isGenerateController()) {
      generated.add(writeFile("controller.vm", resolvePath(moduleName, "web/controller", entity + "Controller.java"), rendered));
    }
    // 9. api/feign
    if (config.isGenerateFeign()) {
      generated.add(writeFile("feign.vm", resolvePath(moduleName, "api", entity + "FeignClient.java"), rendered));
      generated.add(writeFile("assembler.vm", resolvePath(moduleName, "api/assembler", entity + "Assembler.java"), rendered));
      generated.add(writeFile("fallbackFactory.vm", resolvePath(moduleName, "api", entity + "ClientFallbackFactory.java"), rendered));
    }

    generated.removeIf(path -> path == null || path.isBlank());
    log.info("表 {} 生成完成，共 {} 个文件", table.getTableName(), generated.size());
    return generated;
  }

  /**
   * 渲染全部模板（不写文件）。
   *
   * @param table - 表元数据
   * @return 模板名 → 渲染后内容的映射
   */
  private Map<String, String> renderAll(TableMetadata table) {
    ModuleGroupConfig config = properties.resolveEffectiveConfig();
    Map<String, Object> context = buildContext(table);
    Map<String, String> result = new HashMap<>(14);

    for (String templateName : listTemplateNames()) {
      result.put(templateName, renderTemplate(templateName, context));
    }
    return result;
  }

  /**
   * 渲染单个 Velocity 模板。
   *
   * @param templateName - 模板文件名（如 "entity.vm"）
   * @param context - 模板上下文
   * @return 渲染后的字符串
   */
  private String renderTemplate(String templateName, Map<String, Object> context) {
    org.apache.velocity.Template template = velocityEngine.getTemplate(templateName, TEMPLATE_ENCODING);
    VelocityContext vc = new VelocityContext(context);
    java.io.StringWriter writer = new java.io.StringWriter();
    template.merge(vc, writer);
    return writer.toString();
  }

  /**
   * 根据文件冲突策略写入文件。
   *
   * @param templateName - 模板名（用于渲染）
   * @param outputPath - 输出路径
   * @param rendered - 预渲染的模板内容映射
   * @return 实际写入的路径；若跳过则返回 null
   */
  private String writeFile(String templateName, String outputPath, Map<String, String> rendered) {
    ModuleGroupConfig config = properties.resolveEffectiveConfig();
    Path path = Paths.get(outputPath);

    // 检查文件是否已存在
    if (Files.exists(path)) {
      String strategy = config.getFileConflictStrategy();
      switch (strategy) {
        case "skip" -> {
          log.debug("文件已存在，跳过: {}", outputPath);
          return null;
        }
        case "merge" -> {
          // TODO: 实现智能合并（v2.0）
          log.warn("merge 策略尚未实现，降级为 override: {}", outputPath);
        }
        case "prompt" -> {
          // 在 CLI/REST 模式下默认 skip
          log.info("文件已存在（使用 skip 策略）: {}", outputPath);
          return null;
        }
        case "override" -> {
          // 继续执行写入
          break;
        }
        default -> {
          log.warn("未知冲突策略: {}，降级为 override", strategy);
        }
      }
    }

    try {
      Files.createDirectories(path.getParent());
      String content = rendered.getOrDefault(templateName, renderTemplate(templateName, buildContext(null)));
      Files.writeString(path, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new GeneratorWriteException("写入文件失败: " + outputPath, e);
    }
    return outputPath;
  }

  private Map<String, Object> buildContext(TableMetadata table) {
    ModuleGroupConfig config = properties.resolveEffectiveConfig();
    Map<String, Object> ctx = new HashMap<>(16);
    ctx.put("table", table);
    ctx.put("module", config.getModuleName());
    ctx.put("package", config.getPackageName());
    ctx.put("author", config.getAuthor());
    ctx.put("date", LocalDateTime.now().format(DATE_FMT));
    // 包路径段
    ctx.put("domainPackage", config.getPackageName() + ".domain");
    ctx.put("infraPackage", config.getPackageName() + ".infra");
    ctx.put("serverPackage", config.getPackageName() + ".server");
    ctx.put("webPackage", config.getPackageName() + ".web");
    ctx.put("apiPackage", config.getPackageName() + ".api");
    return ctx;
  }

  private String resolvePath(String moduleName, String layer, String fileName) {
    ModuleGroupConfig config = properties.resolveEffectiveConfig();
    // 输出到目标模块的 src/main/java/com/njydsz/{module}/{layer}/{fileName}
    return config.getOutputDir()
        + "/ydsz-" + moduleName + "/src/main/java/com/njydsz/" + moduleName + "/" + layer + "/" + fileName;
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * 代码生成器文件写入异常。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  public static class GeneratorWriteException extends RuntimeException {
    public GeneratorWriteException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
