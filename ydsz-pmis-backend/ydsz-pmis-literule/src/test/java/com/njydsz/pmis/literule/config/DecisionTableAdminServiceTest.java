package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.spi.RuleConfigBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DecisionTableAdminService} 单元测试。
 *
 * <p>覆盖决策表 CRUD、启停、删除、dry-run 等管理操作。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("决策表管理服务测试")
@ExtendWith(MockitoExtension.class)
class DecisionTableAdminServiceTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private DecisionTableConfigProvider configProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RuleConfigBroadcaster broadcaster;

    @InjectMocks
    private DecisionTableAdminService adminService;

    private DecisionTableDefinition buildTable(String code, String name) {
        return DecisionTableDefinition.builder()
                .tableCode(code)
                .tableName(name)
                .hitPolicy(HitPolicy.FIRST)
                .conditionColumns(List.of(
                        DecisionTableDefinition.Column.builder()
                                .name("amount").label("金额").type("number").build()))
                .actionColumns(List.of(
                        DecisionTableDefinition.Column.builder()
                                .name("severity").label("严重度").type("string").build()))
                .rows(List.of())
                .version(1)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("查询：listAll / getByCode")
    class QueryTest {

        @Test
        @DisplayName("正常场景：listAll 委托 configProvider")
        void shouldListAll() {
            List<DecisionTableDefinition> tables = List.of(buildTable("DT001", "决策表1"));
            when(configProvider.loadAllTables()).thenReturn(tables);

            List<DecisionTableDefinition> result = adminService.listAll();

            assertThat(result).isSameAs(tables);
        }

        @Test
        @DisplayName("正常场景：getByCode 委托 configProvider")
        void shouldGetByCode() {
            DecisionTableDefinition table = buildTable("DT001", "决策表1");
            when(configProvider.findByCode("DT001")).thenReturn(table);

            DecisionTableDefinition result = adminService.getByCode("DT001");

            assertThat(result).isSameAs(table);
        }
    }

    @Nested
    @DisplayName("保存：save")
    class SaveTest {

        @Test
        @DisplayName("异常场景：tableCode 为空抛 IllegalArgumentException")
        void shouldThrowWhenTableCodeBlank() {
            DecisionTableDefinition def = buildTable("  ", "决策表");

            assertThatThrownBy(() -> adminService.save(def, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tableCode");
        }

        @Test
        @DisplayName("异常场景：tableName 为空抛 IllegalArgumentException")
        void shouldThrowWhenTableNameBlank() {
            DecisionTableDefinition def = buildTable("DT001", "  ");

            assertThatThrownBy(() -> adminService.save(def, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tableName");
        }

        @Test
        @DisplayName("异常场景：conditionColumns 为空抛 IllegalArgumentException")
        void shouldThrowWhenConditionColumnsEmpty() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT001").tableName("决策表")
                    .conditionColumns(List.of())
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder().name("s").build()))
                    .build();

            assertThatThrownBy(() -> adminService.save(def, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conditionColumns");
        }

        @Test
        @DisplayName("异常场景：actionColumns 为空抛 IllegalArgumentException")
        void shouldThrowWhenActionColumnsEmpty() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT001").tableName("决策表")
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder().name("c").build()))
                    .actionColumns(List.of())
                    .build();

            assertThatThrownBy(() -> adminService.save(def, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("actionColumns");
        }

        @Test
        @DisplayName("正常场景：保存成功并发布事件")
        void shouldSaveAndPublishEvent() {
            DecisionTableDefinition def = buildTable("DT001", "决策表1");
            when(configProvider.save(def, "admin")).thenReturn(def);

            DecisionTableDefinition result = adminService.save(def, "admin", "新增");

            assertThat(result).isNotNull();
            ArgumentCaptor<RuleConfigRefreshEvent> captor = ArgumentCaptor.forClass(RuleConfigRefreshEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getChangeType()).isEqualTo(RuleConfigRefreshEvent.ChangeType.UPDATE);
            assertThat(captor.getValue().getRuleCode()).isEqualTo("DT001");
        }

        @Test
        @DisplayName("正常场景：rows 为 null 时默认空列表")
        void shouldDefaultRowsWhenNull() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT001").tableName("决策表1")
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder().name("c").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder().name("a").build()))
                    .rows(null)
                    .build();
            when(configProvider.save(def, "admin")).thenReturn(def);

            adminService.save(def, "admin", "新增");

            assertThat(def.getRows()).isEmpty();
        }

        @Test
        @DisplayName("正常场景：hitPolicy 为 null 时默认 FIRST")
        void shouldDefaultHitPolicyWhenNull() {
            DecisionTableDefinition def = DecisionTableDefinition.builder()
                    .tableCode("DT001").tableName("决策表1")
                    .conditionColumns(List.of(
                            DecisionTableDefinition.Column.builder().name("c").build()))
                    .actionColumns(List.of(
                            DecisionTableDefinition.Column.builder().name("a").build()))
                    .hitPolicy(null)
                    .build();
            when(configProvider.save(def, "admin")).thenReturn(def);

            adminService.save(def, "admin", "新增");

            assertThat(def.getHitPolicy()).isEqualTo(HitPolicy.FIRST);
        }

        @Test
        @DisplayName("正常场景：配置广播器时同时广播事件")
        void shouldBroadcastWhenConfigured() {
            adminService.setBroadcaster(broadcaster);
            when(broadcaster.isAvailable()).thenReturn(true);
            DecisionTableDefinition def = buildTable("DT001", "决策表1");
            when(configProvider.save(def, "admin")).thenReturn(def);

            adminService.save(def, "admin", "新增");

            verify(broadcaster).broadcast(any(RuleConfigRefreshEvent.class), anyString());
        }
    }

    @Nested
    @DisplayName("启停切换：toggle")
    class ToggleTest {

        @Test
        @DisplayName("正常场景：切换启停并发布事件")
        void shouldToggleAndPublishEvent() {
            adminService.toggle("DT001", false, "admin");

            verify(configProvider).toggleEnabled("DT001", false, "admin");
            ArgumentCaptor<RuleConfigRefreshEvent> captor = ArgumentCaptor.forClass(RuleConfigRefreshEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getChangeType()).isEqualTo(RuleConfigRefreshEvent.ChangeType.TOGGLE);
        }
    }

    @Nested
    @DisplayName("删除：delete")
    class DeleteTest {

        @Test
        @DisplayName("正常场景：删除决策表并注销引擎和发布事件")
        void shouldDeleteAndUnregister() {
            adminService.delete("DT001", "admin");

            verify(configProvider).delete("DT001", "admin");
            verify(ruleEngine).unregister("DT001");
            ArgumentCaptor<RuleConfigRefreshEvent> captor = ArgumentCaptor.forClass(RuleConfigRefreshEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getChangeType()).isEqualTo(RuleConfigRefreshEvent.ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("Dry-run 仿真：dryRun")
    class DryRunTest {

        @Test
        @DisplayName("边界场景：决策表不存在返回 null")
        void shouldReturnNullWhenTableNotFound() {
            when(configProvider.findByCode("DT_NOT_EXIST")).thenReturn(null);

            RuleResult result = adminService.dryRun("DT_NOT_EXIST", Map.of());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("正常场景：执行 dryRun 返回评估结果")
        void shouldDryRun() {
            DecisionTableDefinition def = buildTable("DT001", "决策表1");
            when(configProvider.findByCode("DT001")).thenReturn(def);

            RuleResult result = adminService.dryRun("DT001", Map.of("amount", 2000));

            assertThat(result).isNotNull();
            assertThat(result.getRuleCode()).isEqualTo("DT001");
        }
    }

    @Nested
    @DisplayName("Excel 导出：exportExcel")
    class ExportExcelTest {

        @Test
        @DisplayName("异常场景：决策表不存在抛 IllegalArgumentException")
        void shouldThrowWhenTableNotFound() {
            when(configProvider.findByCode("DT_NOT_EXIST")).thenReturn(null);

            assertThatThrownBy(() -> adminService.exportExcel("DT_NOT_EXIST"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("决策表不存在");
        }
    }
}
