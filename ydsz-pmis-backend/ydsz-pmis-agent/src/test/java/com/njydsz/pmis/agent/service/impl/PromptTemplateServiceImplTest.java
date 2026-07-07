package com.njydsz.pmis.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.PromptTemplateCreateDTO;
import com.njydsz.pmis.agent.dto.PromptTemplateQueryDTO;
import com.njydsz.pmis.agent.engine.prompt.PromptTemplateRegistry;
import com.njydsz.pmis.agent.entity.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.mapper.AgentPromptTemplateMapper;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prompt 模板管理服务实现单元测试（P2-2 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>create：字段映射、version 默认值、isActive=false、tenantId=1</li>
 *   <li>activate：模板不存在抛 BizException；存在则排他+激活+刷新缓存</li>
 *   <li>getById：调用 selectById</li>
 *   <li>page：条件过滤、分页参数兜底、PageResult 转换</li>
 *   <li>delete：模板不存在直接返回；存在则删除；生效模板删除后刷新缓存</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@DisplayName("PromptTemplateServiceImpl 模板管理服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class PromptTemplateServiceImplTest {

    @Mock
    private AgentPromptTemplateMapper mapper;

    @Mock
    private PromptTemplateRegistry registry;

    @InjectMocks
    private PromptTemplateServiceImpl service;

    // ==================== 辅助方法 ====================

    /** 构造创建 DTO */
    private PromptTemplateCreateDTO createDto(String code, String content, String version) {
        PromptTemplateCreateDTO dto = new PromptTemplateCreateDTO();
        dto.setTemplateCode(code);
        dto.setTemplateName("测试模板-" + code);
        dto.setAgentType("FLOW_GENERATOR");
        dto.setPromptRole("SYSTEM");
        dto.setContent(content);
        if (version != null) {
            dto.setVersion(version);
        }
        dto.setDescription("测试描述");
        return dto;
    }

    /** 构造 DB 模板实体 */
    private AgentPromptTemplateDO templateDO(String id, String code, String version, Boolean isActive) {
        AgentPromptTemplateDO t = new AgentPromptTemplateDO();
        t.setId(id);
        t.setTemplateCode(code);
        t.setTemplateName("模板-" + code);
        t.setAgentType("FLOW_GENERATOR");
        t.setPromptRole("SYSTEM");
        t.setContent("Hello ${name}");
        t.setVersion(version);
        t.setIsActive(isActive);
        return t;
    }

    // ==================== create 测试 ====================

    @Nested
    @DisplayName("create 创建模板测试")
    class CreateTest {

        @Test
        @DisplayName("正常创建：字段正确映射、version 默认 1.0.0、isActive=false、tenantId=1")
        void shouldCreateWithDefaults() {
            PromptTemplateCreateDTO dto = createDto("CODE1", "Hello ${name}", null);

            AgentPromptTemplateDO result = service.create(dto);

            assertThat(result).isNotNull();
            assertThat(result.getTemplateCode()).isEqualTo("CODE1");
            assertThat(result.getTemplateName()).isEqualTo("测试模板-CODE1");
            assertThat(result.getAgentType()).isEqualTo("FLOW_GENERATOR");
            assertThat(result.getPromptRole()).isEqualTo("SYSTEM");
            assertThat(result.getContent()).isEqualTo("Hello ${name}");
            assertThat(result.getVersion()).isEqualTo("1.0.0");
            assertThat(result.getIsActive()).isFalse();
            assertThat(result.getTenantId()).isEqualTo("1");

            verify(mapper, times(1)).insert(any(AgentPromptTemplateDO.class));
        }

        @Test
        @DisplayName("指定 version 时使用指定值")
        void shouldUseSpecifiedVersion() {
            PromptTemplateCreateDTO dto = createDto("CODE2", "Hi", "2.1.0");

            AgentPromptTemplateDO result = service.create(dto);

            assertThat(result.getVersion()).isEqualTo("2.1.0");
            verify(mapper, times(1)).insert(any(AgentPromptTemplateDO.class));
        }

        @Test
        @DisplayName("version 为空串时使用默认 1.0.0")
        void shouldUseDefaultWhenVersionEmpty() {
            PromptTemplateCreateDTO dto = createDto("CODE3", "Hi", "");

            AgentPromptTemplateDO result = service.create(dto);

            assertThat(result.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("新增模板 isActive 始终为 false（即使后续需要 activate）")
        void shouldSetIsActiveFalseOnCreate() {
            PromptTemplateCreateDTO dto = createDto("CODE4", "Hi", null);

            AgentPromptTemplateDO result = service.create(dto);

            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("插入实体的字段通过 ArgumentCaptor 验证")
        void shouldInsertCorrectEntityFields() {
            PromptTemplateCreateDTO dto = createDto("CODE5", "Hello ${name}", "1.2.0");

            service.create(dto);

            ArgumentCaptor<AgentPromptTemplateDO> captor =
                    ArgumentCaptor.forClass(AgentPromptTemplateDO.class);
            verify(mapper).insert(captor.capture());

            AgentPromptTemplateDO inserted = captor.getValue();
            assertThat(inserted.getTemplateCode()).isEqualTo("CODE5");
            assertThat(inserted.getVersion()).isEqualTo("1.2.0");
            assertThat(inserted.getIsActive()).isFalse();
            assertThat(inserted.getTenantId()).isEqualTo("1");
        }
    }

    // ==================== activate 测试 ====================

    @Nested
    @DisplayName("activate 激活模板测试")
    class ActivateTest {

        @Test
        @DisplayName("模板不存在时抛 BizException")
        void shouldThrowBizExceptionWhenTemplateNotFound() {
            when(mapper.selectById("non-existent")).thenReturn(null);

            assertThatThrownBy(() -> service.activate("non-existent"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("模板不存在");

            verify(mapper, never()).deactivateOthers(anyString(), anyString());
            verify(mapper, never()).updateById(any(AgentPromptTemplateDO.class));
            verify(registry, never()).refresh();
        }

        @Test
        @DisplayName("正常激活：排他 + 激活当前 + 刷新缓存")
        void shouldActivateCorrectly() {
            AgentPromptTemplateDO t = templateDO("ID1", "CODE_A", "1.0.0", false);
            when(mapper.selectById("ID1")).thenReturn(t);

            AgentPromptTemplateDO result = service.activate("ID1");

            assertThat(result.getIsActive()).isTrue();

            // 验证排他调用
            verify(mapper, times(1)).deactivateOthers("CODE_A", "ID1");
            // 验证更新调用
            verify(mapper, times(1)).updateById(any(AgentPromptTemplateDO.class));
            // 验证刷新缓存
            verify(registry, times(1)).refresh();
        }

        @Test
        @DisplayName("激活时排他使用正确的 code 和 excludeId")
        void shouldDeactivateOthersWithCorrectArgs() {
            AgentPromptTemplateDO t = templateDO("ID2", "CODE_B", "2.0.0", false);
            when(mapper.selectById("ID2")).thenReturn(t);

            service.activate("ID2");

            verify(mapper).deactivateOthers(eq("CODE_B"), eq("ID2"));
        }
    }

    // ==================== getById 测试 ====================

    @Nested
    @DisplayName("getById 查询测试")
    class GetByIdTest {

        @Test
        @DisplayName("模板存在时返回模板实体")
        void shouldReturnTemplateWhenExists() {
            AgentPromptTemplateDO t = templateDO("ID3", "CODE_C", "1.0.0", true);
            when(mapper.selectById("ID3")).thenReturn(t);

            AgentPromptTemplateDO result = service.getById("ID3");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("ID3");
            assertThat(result.getTemplateCode()).isEqualTo("CODE_C");
        }

        @Test
        @DisplayName("模板不存在时返回 null")
        void shouldReturnNullWhenNotFound() {
            when(mapper.selectById(anyString())).thenReturn(null);

            AgentPromptTemplateDO result = service.getById("non-existent");

            assertThat(result).isNull();
        }
    }

    // ==================== page 测试 ====================

    @Nested
    @DisplayName("page 分页查询测试")
    class PageTest {

        @Test
        @DisplayName("正常分页查询返回 PageResult")
        void shouldReturnPageResult() {
            Page<AgentPromptTemplateDO> mockPage = new Page<>(1, 20);
            List<AgentPromptTemplateDO> records = List.of(
                    templateDO("ID1", "CODE1", "1.0.0", true),
                    templateDO("ID2", "CODE2", "1.0.0", false)
            );
            mockPage.setRecords(records);
            mockPage.setTotal(2);
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            query.setPage(1);
            query.setSize(20);
            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result).isNotNull();
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("page=null 时使用默认值 1")
        void shouldUseDefaultPageWhenNull() {
            Page<AgentPromptTemplateDO> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            query.setPage(null);
            query.setSize(20);

            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result.getPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("size=null 时使用默认值 20")
        void shouldUseDefaultSizeWhenNull() {
            Page<AgentPromptTemplateDO> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            query.setPage(1);
            query.setSize(null);

            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("page=0 时使用默认值 1")
        void shouldUseDefaultPageWhenZero() {
            Page<AgentPromptTemplateDO> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            query.setPage(0);
            query.setSize(10);

            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result.getPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("size=0 时使用默认值 20")
        void shouldUseDefaultSizeWhenZero() {
            Page<AgentPromptTemplateDO> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            query.setPage(1);
            query.setSize(0);

            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("selectPage 返回 null 时 PageResult.ofPage 返回 empty")
        void shouldReturnEmptyWhenSelectPageReturnsNull() {
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result).isNotNull();
            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
        }

        @Test
        @DisplayName("设置所有查询条件时不抛异常（验证 wrapper 构建路径）")
        void shouldBuildWrapperWithAllConditions() {
            // mockPage 的 current/size 需与传入 query 一致，PageResult.ofPage 使用 p.getCurrent()/getSize()
            Page<AgentPromptTemplateDO> mockPage = new Page<>(2, 50);
            mockPage.setRecords(Collections.emptyList());
            when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            PromptTemplateQueryDTO query = new PromptTemplateQueryDTO();
            query.setTemplateCode("FLOW");
            query.setAgentType("FLOW_GENERATOR");
            query.setPromptRole("SYSTEM");
            query.setIsActive(true);
            query.setPage(2);
            query.setSize(50);

            PageResult<AgentPromptTemplateDO> result = service.page(query);

            assertThat(result).isNotNull();
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(50);
            verify(mapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ==================== delete 测试 ====================

    @Nested
    @DisplayName("delete 删除测试")
    class DeleteTest {

        @Test
        @DisplayName("模板不存在时直接返回，不调用 deleteById")
        void shouldReturnDirectlyWhenTemplateNotFound() {
            when(mapper.selectById("non-existent")).thenReturn(null);

            service.delete("non-existent");

            verify(mapper, never()).deleteById(anyString());
            verify(registry, never()).refresh();
        }

        @Test
        @DisplayName("删除非生效模板时不刷新缓存")
        void shouldNotRefreshCacheWhenDeletingInactiveTemplate() {
            AgentPromptTemplateDO t = templateDO("ID1", "CODE1", "1.0.0", false);
            when(mapper.selectById("ID1")).thenReturn(t);

            service.delete("ID1");

            verify(mapper, times(1)).deleteById("ID1");
            verify(registry, never()).refresh();
        }

        @Test
        @DisplayName("删除生效模板时刷新缓存")
        void shouldRefreshCacheWhenDeletingActiveTemplate() {
            AgentPromptTemplateDO t = templateDO("ID2", "CODE2", "1.0.0", true);
            when(mapper.selectById("ID2")).thenReturn(t);

            service.delete("ID2");

            verify(mapper, times(1)).deleteById("ID2");
            verify(registry, times(1)).refresh();
        }

        @Test
        @DisplayName("isActive=null 时按非生效处理（不刷新缓存）")
        void shouldNotRefreshCacheWhenIsActiveNull() {
            AgentPromptTemplateDO t = templateDO("ID3", "CODE3", "1.0.0", null);
            when(mapper.selectById("ID3")).thenReturn(t);

            service.delete("ID3");

            verify(mapper, times(1)).deleteById("ID3");
            verify(registry, never()).refresh();
        }
    }
}
