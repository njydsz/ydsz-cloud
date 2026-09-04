package com.njydsz.generator.service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.stereotype.Service;

import com.njydsz.generator.config.GeneratorProperties;
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
 *   <li>infra/repository/{EntityName}RepositoryImpl.java</li>
 *   <li>server/service/{EntityName}Service.java</li>
 *   <li>server/service/impl/{EntityName}ServiceImpl.java</li>
 *   <li>web/controller/{EntityName}Controller.java</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGeneratorService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
    List<TableMetadata> tables = metadataReader.readAllConfiguredTables();
    List<String> generated = new java.util.ArrayList<>(tables.size() * 8);
    for (TableMetadata table : tables) {
      generated.addAll(generateAll(table));
    }
    return generated;
  }

  // -----------------------------------------------------------------------

  private List<String> generateAll(TableMetadata table) {
    List<String> generated = new java.util.ArrayList<>(8);
    String entity = table.getEntityName();
    String moduleName = properties.getModuleName();

    Map<String, Object> context = buildContext(table);

    // 1. domain/entity
    if (properties.isGenerateEntity()) {
      generated.add(renderWrite("entity.vm", resolvePath(moduleName, "domain/entity", entity + ".java"), context));
    }
    // 2. domain/dto
    if (properties.isGenerateModel()) {
      generated.add(renderWrite("dto.vm", resolvePath(moduleName, "domain/dto", entity + "DTO.java"), context));
      generated.add(renderWrite("vo.vm", resolvePath(moduleName, "domain/vo", entity + "VO.java"), context));
      generated.add(renderWrite("query.vm", resolvePath(moduleName, "domain/query", entity + "PageQuery.java"), context));
    }
    // 3. domain/repository
    if (properties.isGenerateRepository()) {
      generated.add(renderWrite("repository.vm", resolvePath(moduleName, "domain/repository", entity + "Repository.java"), context));
    }
    // 4. infra/repository
    if (properties.isGenerateRepository()) {
      generated.add(renderWrite("repositoryImpl.vm", resolvePath(moduleName, "infra/repository", entity + "RepositoryImpl.java"), context));
    }
    // 5. server/service
    if (properties.isGenerateService()) {
      generated.add(renderWrite("service.vm", resolvePath(moduleName, "server/service", entity + "Service.java"), context));
      generated.add(renderWrite("serviceImpl.vm", resolvePath(moduleName, "server/service/impl", entity + "ServiceImpl.java"), context));
    }
    // 6. web/controller
    if (properties.isGenerateController()) {
      generated.add(renderWrite("controller.vm", resolvePath(moduleName, "web/controller", entity + "Controller.java"), context));
    }

    log.info("表 {} 生成完成，共 {} 个文件", table.getTableName(), generated.size());
    return generated;
  }

  private Map<String, Object> buildContext(TableMetadata table) {
    Map<String, Object> ctx = new HashMap<>(16);
    ctx.put("table", table);
    ctx.put("module", properties.getModuleName());
    ctx.put("package", properties.getPackageName());
    ctx.put("author", properties.getAuthor());
    ctx.put("date", LocalDateTime.now().format(DATE_FMT));
    // 包路径段
    ctx.put("domainPackage", properties.getPackageName() + ".domain");
    ctx.put("infraPackage", properties.getPackageName() + ".infra");
    ctx.put("serverPackage", properties.getPackageName() + ".server");
    ctx.put("webPackage", properties.getPackageName() + ".web");
    return ctx;
  }

  private String renderWrite(String templateName, String outputPath, Map<String, Object> context) {
    StringWriter writer = new StringWriter();
    org.apache.velocity.Template template = velocityEngine.getTemplate(templateName, "UTF-8");
    VelocityContext vc = new VelocityContext(context);
    template.merge(vc, writer);

    try {
      Path path = Paths.get(outputPath);
      Files.createDirectories(path.getParent());
      Files.writeString(path, writer.toString(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("写入文件失败: " + outputPath, e);
    }
    return outputPath;
  }

  private String resolvePath(String moduleName, String layer, String fileName) {
    // 输出到目标模块的 src/main/java/com/njydsz/{module}/{layer}/{fileName}
    return properties.getOutputDir()
        + "/ydsz-" + moduleName + "/src/main/java/com/njydsz/" + moduleName + "/" + layer + "/" + fileName;
  }
}
