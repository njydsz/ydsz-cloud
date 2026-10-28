package com.njydsz.generator.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 生成器模板的"不生成违规代码"门禁。
 *
 * <p>背景（见报告 B1-5）：生成器是新增模块的**唯一入口**，模板里写了什么，下游就会缺什么、错什么。
 * 实测 `default` 组 18 个模板中有 6 个仍在产出 Spring Boot 2 时代的代码：
 * {@code javax.validation.*}（Boot 4 已改 {@code jakarta.*}）、{@code io.swagger.annotations.*}
 * （已换 springdoc 的 {@code io.swagger.v3.oas.annotations}）、以及并不存在的
 * {@code com.njydsz.common.core.page.TableDataInfo}（统一分页类型是 {@code PageResponse}）。
 * 照着生成的代码**编译不过**，而"没有测试模板"又让新模块从第一天起就是零测试。
 *
 * <p>本测试把这两件事变成可执行断言：扫描 classpath 下 {@code templates/**} 的全部 {@code .vm}，
 * 命中禁用符号即红；同时要求至少存在一个测试模板，避免"新模块零测试"的缺口被再生。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("代码生成器模板：不得产出违规/不可编译代码")
class GeneratorTemplateComplianceTest {

  /** 模板根目录（相对 classpath） */
  private static final String TEMPLATE_ROOT = "templates";

  @Test
  @DisplayName("模板不得引用 javax.* / Swagger2 注解 / 不存在的 TableDataInfo")
  void templates_should_not_emit_forbidden_symbols() throws IOException, URISyntaxException {
    List<Path> templates = listTemplates();
    assertThat(templates).as("模板数量应大于 10（目录扫不到不等于没问题）").hasSizeGreaterThan(10);

    String[] forbidden = {
      "javax.", "io.swagger.annotations", "TableDataInfo", "@Api(", "@ApiModel(", "@ApiOperation("
    };
    List<String> violations = new ArrayList<>();
    for (Path template : templates) {
      String content = Files.readString(template, StandardCharsets.UTF_8);
      for (String token : forbidden) {
        if (content.contains(token)) {
          violations.add(template.getFileName() + " → " + token);
        }
      }
    }
    assertThat(violations)
        .as("模板仍在产出违规代码（Boot 4 已用 jakarta/springdoc，统一分页是 PageResponse）")
        .isEmpty();
  }

  @Test
  @DisplayName("DTO 模板必须使用 jakarta 校验注解与 springdoc @Schema")
  void dto_template_should_use_jakarta_and_springdoc() throws IOException, URISyntaxException {
    String dto = readTemplate("default/dto.vm");

    assertThat(dto).contains("jakarta.validation").contains("io.swagger.v3.oas.annotations.media.Schema");
  }

  @Test
  @DisplayName("必须存在测试模板：新模块从第一天就带测试骨架")
  void should_provide_a_test_template() throws IOException, URISyntaxException {
    List<String> testTemplates =
        listTemplates().stream()
            .map(p -> p.getFileName().toString())
            .filter(name -> name.endsWith("Test.vm"))
            .toList();

    assertThat(testTemplates)
        .as("缺少测试模板 → 新生成的模块必然从零测试起步（报告 B1-5 的缺口会再生）")
        .isNotEmpty();
  }

  @Test
  @DisplayName("测试模板继承平台测试基类，并对分页端点断言统一信封")
  void test_template_should_follow_platform_smoke_pattern() throws IOException, URISyntaxException {
    String testTpl = readTemplate("default/controllerMockTest.vm");

    assertThat(testTpl).contains("BaseControllerMockTest").contains("standaloneSetup");
    assertThat(testTpl).contains("SUCCESS_CODE");
  }

  @Test
  @DisplayName("模板 import 的 com.njydsz.* 类必须真实存在")
  void template_imports_should_exist() throws IOException, URISyntaxException {
    ClassLoader loader = getClass().getClassLoader();
    List<String> violations = new ArrayList<>();
    for (Path template : listTemplates()) {
      for (String fqcn : extractNjydszImports(Files.readString(template, StandardCharsets.UTF_8))) {
        try {
          Class.forName(fqcn, false, loader);
        } catch (ClassNotFoundException e) {
          violations.add(template.getFileName() + " → " + fqcn);
        }
      }
    }
    assertThat(violations)
        .as("模板引用了不存在的类 → 生成出来的代码必然编译不过")
        .isEmpty();
  }

  @Test
  @DisplayName("模板不得依赖未声明的 PageHelper（分页统一走 MyBatis-Plus / PageResponse）")
  void templates_should_not_use_pagehelper() throws IOException, URISyntaxException {
    List<String> violations = new ArrayList<>();
    for (Path template : listTemplates()) {
      if (Files.readString(template, StandardCharsets.UTF_8).contains("com.github.pagehelper")) {
        violations.add(template.getFileName().toString());
      }
    }
    assertThat(violations).as("PageHelper 未在父 pom 声明，生成代码无法解析").isEmpty();
  }

  /** 抽取模板中的 {@code import com.njydsz.Xxx;} 全限定名（跳过含 Velocity 占位符的动态 import）。 */
  private static List<String> extractNjydszImports(String content) {
    List<String> fqcns = new ArrayList<>();
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("import ") || !trimmed.endsWith(";")) {
        continue;
      }
      String fqcn = trimmed.substring("import ".length(), trimmed.length() - 1).trim();
      if (fqcn.startsWith("com.njydsz.") && !fqcn.contains("$")) {
        fqcns.add(fqcn);
      }
    }
    return fqcns;
  }

  private static String readTemplate(String relative) throws IOException, URISyntaxException {
    URL url =
        GeneratorTemplateComplianceTest.class
            .getClassLoader()
            .getResource(TEMPLATE_ROOT + "/" + relative);
    assertThat(url).as("模板缺失：%s", relative).isNotNull();
    return Files.readString(Path.of(url.toURI()), StandardCharsets.UTF_8);
  }

  private static List<Path> listTemplates() throws IOException, URISyntaxException {
    URL url = GeneratorTemplateComplianceTest.class.getClassLoader().getResource(TEMPLATE_ROOT);
    assertThat(url).as("classpath 下找不到 %s 目录", TEMPLATE_ROOT).isNotNull();
    Path root = Path.of(url.toURI());
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(p -> p.toString().endsWith(".vm")).toList();
    }
  }
}
