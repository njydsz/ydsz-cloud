package com.njydsz.generator.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 代码生成器配置属性。
 *
 * <p>配置前缀：{@code ydsz.generator}。支持分组模式，可通过 {@code active-group} 切换不同模块配置。
 *
 * <p><b>分组配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   generator:
 *     active-group: dev-system
 *     groups:
 *       dev-system:
 *         module-name: system
 *         package-name: com.njydsz.system
 *         table-names:
 *           - ydsz_sys_tenant
 *       dev-userinfo:
 *         module-name: userinfo
 *         package-name: com.njydsz.userinfo
 *         table-names:
 *           - ydsz_user_info
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.generator")
public class GeneratorProperties {

  /** 当前激活的分组名（对应 {@code groups} 中的 key） */
  private String activeGroup;

  /** 分组配置映射（key = 分组名，value = 模块配置） */
  private Map<String, ModuleGroupConfig> groups = new HashMap<>();

  /** 生成目标模块名（如 {@code system}、{@code userinfo}） */
  @NotBlank
  private String moduleName;

  /** 生成目标包名前缀（如 {@code com.njydsz.system}） */
  @NotBlank
  private String packageName;

  /** 目标表名列表 */
  @NotEmpty
  private List<String> tableNames;

  /** 表名前缀（生成的实体类名去除该前缀，如 {@code ydsz_}） */
  private String tablePrefix = "ydsz_";

  /** 生成输出目录（绝对路径） */
  @NotBlank
  private String outputDir;

  /** 是否生成 Controller */
  private boolean generateController = true;

  /** 是否生成 Service */
  private boolean generateService = true;

  /** 是否生成 Repository */
  private boolean generateRepository = true;

  /** 是否生成 Entity */
  private boolean generateEntity = true;

  /** 是否生成 VO/DTO/Query */
  private boolean generateModel = true;

  /** 是否生成 Mapper */
  private boolean generateMapper = true;

  /** 是否生成 Converter */
  private boolean generateConverter = true;

  /** 是否生成 FeignClient */
  private boolean generateFeign = true;

  /** 作者 */
  private String author = "ydsz-team";

  /** 文件已存在时的处理策略：skip / override / merge / prompt */
  private String fileConflictStrategy = "prompt";

  /**
   * 获取当前生效的配置。
   *
   * <p>若 {@code activeGroup} 非空且 {@code groups} 含对应分组，则返回合并后的配置（分组配置优先，顶层配置作兜底）。
   *
   * @return 生效的配置
   */
  public ModuleGroupConfig resolveEffectiveConfig() {
    if (activeGroup != null && !activeGroup.isBlank() && groups.containsKey(activeGroup)) {
      ModuleGroupConfig group = groups.get(activeGroup);
      return mergeWithFallback(group, this);
    }
    return fromTopLevel(this);
  }

  private static ModuleGroupConfig mergeWithFallback(ModuleGroupConfig group, GeneratorProperties top) {
    ModuleGroupConfig merged = new ModuleGroupConfig();
    merged.setModuleName(coalesce(group.getModuleName(), top.getModuleName()));
    merged.setPackageName(coalesce(group.getPackageName(), top.getPackageName()));
    merged.setTableNames(group.getTableNames() != null ? group.getTableNames() : top.getTableNames());
    merged.setTablePrefix(group.getTablePrefix() != null ? group.getTablePrefix() : top.getTablePrefix());
    merged.setOutputDir(coalesce(group.getOutputDir(), top.getOutputDir()));
    merged.setAuthor(group.getAuthor() != null ? group.getAuthor() : top.getAuthor());
    merged.setGenerateController(group.isGenerateController());
    merged.setGenerateService(group.isGenerateService());
    merged.setGenerateRepository(group.isGenerateRepository());
    merged.setGenerateEntity(group.isGenerateEntity());
    merged.setGenerateModel(group.isGenerateModel());
    merged.setGenerateMapper(group.isGenerateMapper());
    merged.setGenerateConverter(group.isGenerateConverter());
    merged.setGenerateFeign(group.isGenerateFeign());
    merged.setFileConflictStrategy(group.getFileConflictStrategy());
    return merged;
  }

  private static ModuleGroupConfig fromTopLevel(GeneratorProperties top) {
    ModuleGroupConfig config = new ModuleGroupConfig();
    config.setModuleName(top.getModuleName());
    config.setPackageName(top.getPackageName());
    config.setTableNames(top.getTableNames());
    config.setTablePrefix(top.getTablePrefix());
    config.setOutputDir(top.getOutputDir());
    config.setAuthor(top.getAuthor());
    config.setGenerateController(top.isGenerateController());
    config.setGenerateService(top.isGenerateService());
    config.setGenerateRepository(top.isGenerateRepository());
    config.setGenerateEntity(top.isGenerateEntity());
    config.setGenerateModel(top.isGenerateModel());
    config.setGenerateMapper(top.isGenerateMapper());
    config.setGenerateConverter(top.isGenerateConverter());
    config.setGenerateFeign(top.isGenerateFeign());
    config.setFileConflictStrategy(top.getFileConflictStrategy());
    return config;
  }

  private static String coalesce(String first, String fallback) {
    return (first != null && !first.isBlank()) ? first : fallback;
  }

  /**
   * 模块分组配置。
   *
   * <p>对应 {@code ydsz.generator.groups.<name>} 下的配置项。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  @Data
  public static class ModuleGroupConfig {
    /** 生成目标模块名 */
    private String moduleName;
    /** 生成目标包名前缀 */
    private String packageName;
    /** 目标表名列表 */
    private List<String> tableNames;
    /** 表名前缀 */
    private String tablePrefix = "ydsz_";
    /** 生成输出目录（绝对路径） */
    private String outputDir;
    /** 作者 */
    private String author = "ydsz-team";
    /** 是否生成 Controller */
    private boolean generateController = true;
    /** 是否生成 Service */
    private boolean generateService = true;
    /** 是否生成 Repository */
    private boolean generateRepository = true;
    /** 是否生成 Entity */
    private boolean generateEntity = true;
    /** 是否生成 VO/DTO/Query */
    private boolean generateModel = true;
    /** 是否生成 Mapper */
    private boolean generateMapper = true;
    /** 是否生成 Converter */
    private boolean generateConverter = true;
    /** 是否生成 FeignClient */
    private boolean generateFeign = true;
    /** 文件冲突策略 */
    private String fileConflictStrategy = "prompt";
  }
}
