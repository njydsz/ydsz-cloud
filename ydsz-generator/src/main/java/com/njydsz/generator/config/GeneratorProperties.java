package com.njydsz.generator.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 代码生成器配置属性。
 *
 * <p>配置前缀：{@code ydsz.generator}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.generator")
public class GeneratorProperties {

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

  /** 作者 */
  private String author = "ydsz-team";
}
