package com.njydsz.generator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.repository.GenTemplateGroupRepository;
import com.njydsz.generator.repository.GenTemplateRepository;

/**
 * {@link TemplateDataInitializer} 的补齐语义测试。
 *
 * <p>钉住的是报告 B1-5 的关键行为：内置模板同步必须**逐文件补齐**。
 * 旧实现"分组里只要有一条模板就整组 return"，后果是新增的内置模板（本次新增的
 * `controllerMockTest.vm`）以及模板修复在存量环境里永远不会生效 —— 修复只对全新建库有效，
 * 而真实环境几乎都是存量的。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("生成器内置模板同步：缺哪个补哪个、已存在不覆盖")
class TemplateDataInitializerTest {

  private static final String GROUP_ID = "group-default";

  @Test
  @DisplayName("空库时把所有 classpath 模板落库，且包含测试模板")
  void should_import_all_built_in_templates_when_group_is_empty() {
    GenTemplateRepository templateRepository = mock(GenTemplateRepository.class);
    when(templateRepository.findByGroupIdOrderByFileNameAsc(GROUP_ID)).thenReturn(List.of());

    initializer(templateRepository).onApplicationReady();

    ArgumentCaptor<GenTemplate> captor = ArgumentCaptor.forClass(GenTemplate.class);
    verify(templateRepository, times(countBuiltInTemplates())).save(captor.capture());
    List<String> fileNames = captor.getAllValues().stream().map(GenTemplate::getFileName).toList();

    assertThat(fileNames)
        .as("必须包含测试模板，否则新模块仍然从零测试起步")
        .contains("controllerMockTest.vm")
        .contains("entity.vm");
    assertThat(captor.getAllValues())
        .allSatisfy(
            tpl -> {
              assertThat(tpl.getGroupId()).isEqualTo(GROUP_ID);
              assertThat(tpl.getContent()).isNotBlank();
            });
  }

  @Test
  @DisplayName("库里已有同名模板时跳过该文件，其余仍补齐")
  void should_skip_existing_file_names_and_still_add_new_ones() {
    GenTemplateRepository templateRepository = mock(GenTemplateRepository.class);
    GenTemplate existing = GenTemplate.builder().fileName("entity.vm").groupId(GROUP_ID).build();
    when(templateRepository.findByGroupIdOrderByFileNameAsc(GROUP_ID)).thenReturn(List.of(existing));

    initializer(templateRepository).onApplicationReady();

    ArgumentCaptor<GenTemplate> captor = ArgumentCaptor.forClass(GenTemplate.class);
    int expectedSaves = countBuiltInTemplates() - 1;
    verify(templateRepository, times(expectedSaves)).save(captor.capture());

    assertThat(captor.getAllValues())
        .as("已存在的内置模板不得被覆盖（可能是人工改过的）")
        .extracting(GenTemplate::getFileName)
        .doesNotContain("entity.vm")
        .contains("controllerMockTest.vm");
  }

  @Test
  @DisplayName("分组下没有任何资源时不产生写入")
  void should_do_nothing_when_no_group_matches() {
    GenTemplateRepository templateRepository = mock(GenTemplateRepository.class);
    when(templateRepository.findByGroupIdOrderByFileNameAsc(any())).thenReturn(List.of());
    GenTemplateGroup unknown = mock(GenTemplateGroup.class);
    when(unknown.getId()).thenReturn("other");
    when(unknown.getName()).thenReturn("no-such-group");
    GenTemplateGroupRepository groupRepository = mock(GenTemplateGroupRepository.class);
    when(groupRepository.findAllByOrderBysortAsc()).thenReturn(List.of(unknown));

    new TemplateDataInitializer(groupRepository, templateRepository).onApplicationReady();

    verify(templateRepository, never()).save(any());
  }

  private static TemplateDataInitializer initializer(GenTemplateRepository templateRepository) {
    GenTemplateGroup group = mock(GenTemplateGroup.class);
    when(group.getId()).thenReturn(GROUP_ID);
    when(group.getName()).thenReturn("default");
    GenTemplateGroupRepository groupRepository = mock(GenTemplateGroupRepository.class);
    when(groupRepository.findAllByOrderBysortAsc()).thenReturn(List.of(group));
    return new TemplateDataInitializer(groupRepository, templateRepository);
  }

  /**
   * 本模块测试夹具里的 .vm 数量（{@code src/test/resources/templates/default/}）。
   *
   * <p>真实模板集在 {@code ydsz-generator-server} 的 main resources 里（19 个），运行时才与
   * {@code TemplateDataInitializer} 同处一个 boot 应用的 classpath；单测里用 2 个夹具即可证明
   * "缺哪个补哪个"的语义，真实模板集的合规性由 {@code GeneratorTemplateComplianceTest} 守护。
   */
  private static int countBuiltInTemplates() {
    return 2;
  }
}
