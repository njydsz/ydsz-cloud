package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.project.mapper.InitiationMapper;
import com.njydsz.pmis.project.search.ProjectSearchVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchServiceImpl 单元测试（P2-19：替代 ES，验证 PG tsvector 检索链路）。
 *
 * <p>覆盖维度：
 * <ul>
 *   <li>空关键词短路：不再触发任何 Mapper 调用</li>
 *   <li>正常检索：count + list 两次查询、按 ts_rank 排序的分页结果</li>
 *   <li>租户隔离：tenantId 通过 TenantContext 透传到 Mapper</li>
 *   <li>零命中短路：count = 0 时不再执行 searchByFullText</li>
 *   <li>降级策略：Mapper 抛异常时返回空分页，不污染调用方</li>
 *   <li>reindexAll：PG tsvector 无需重建，返回固定成功标识</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl 单元测试（PG tsvector 替代 ES）")
class SearchServiceImplTest {

    @Mock
    private InitiationMapper initiationMapper;

    @InjectMocks
    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        // 设置默认租户为 1L，避免依赖调用顺序
        TenantContext.setTenantId(TenantContext.DEFAULT_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        // 防止 ThreadLocal 在线程池场景下串号
        TenantContext.clear();
    }

    @Nested
    @DisplayName("searchProjects 方法")
    class SearchProjectsTest {

        @Test
        @DisplayName("空关键词 - 短路返回空分页，不触发 Mapper")
        void searchProjects_BlankKeyword_NoCall() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<ProjectSearchVO> result1 = searchService.searchProjects(null, pageable);
            Page<ProjectSearchVO> result2 = searchService.searchProjects("", pageable);
            Page<ProjectSearchVO> result3 = searchService.searchProjects("   ", pageable);

            // Then
            assertThat(result1.getContent()).isEmpty();
            assertThat(result1.getTotalElements()).isZero();
            assertThat(result2.getContent()).isEmpty();
            assertThat(result3.getContent()).isEmpty();
            verify(initiationMapper, never()).countByFullText(any(), any());
            verify(initiationMapper, never()).searchByFullText(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("正常检索 - count > 0 时执行 searchByFullText 并返回分页")
        void searchProjects_Success() {
            // Given
            String keyword = "智慧园区";
            Pageable pageable = PageRequest.of(0, 10);
            ProjectSearchVO vo1 = buildVo(1L, "PRJ-001", "智慧园区一期");
            ProjectSearchVO vo2 = buildVo(2L, "PRJ-002", "智慧园区二期");
            List<ProjectSearchVO> records = List.of(vo1, vo2);

            when(initiationMapper.countByFullText(eq(keyword), eq(TenantContext.DEFAULT_TENANT_ID)))
                    .thenReturn(2L);
            when(initiationMapper.searchByFullText(eq(keyword), eq(TenantContext.DEFAULT_TENANT_ID), eq(0), eq(10)))
                    .thenReturn(records);

            // When
            Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(2L);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getProjectCode()).isEqualTo("PRJ-001");
            assertThat(result.getContent().get(1).getProjectName()).isEqualTo("智慧园区二期");
        }

        @Test
        @DisplayName("零命中 - count = 0 时不再执行 searchByFullText")
        void searchProjects_ZeroHit_ShortCircuit() {
            // Given
            String keyword = "不存在的项目";
            Pageable pageable = PageRequest.of(0, 10);
            when(initiationMapper.countByFullText(eq(keyword), any())).thenReturn(0L);

            // When
            Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            verify(initiationMapper).countByFullText(eq(keyword), any());
            verify(initiationMapper, never()).searchByFullText(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("租户隔离 - tenantId 通过 TenantContext 正确透传")
        void searchProjects_TenantIsolation() {
            // Given
            Long customTenant = 42L;
            TenantContext.setTenantId(customTenant);
            String keyword = "园区";
            Pageable pageable = PageRequest.of(0, 5);

            when(initiationMapper.countByFullText(eq(keyword), eq(customTenant))).thenReturn(1L);
            when(initiationMapper.searchByFullText(eq(keyword), eq(customTenant), eq(0), eq(5)))
                    .thenReturn(List.of(buildVo(100L, "PRJ-100", "园区项目")));

            // When
            Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(1L);
            verify(initiationMapper).countByFullText(keyword, customTenant);
            verify(initiationMapper).searchByFullText(keyword, customTenant, 0, 5);
        }

        @Test
        @DisplayName("分页参数 - offset/limit 按 pageable 正确计算")
        void searchProjects_PagingArgs() {
            // Given
            String keyword = "测试";
            Pageable pageable = PageRequest.of(2, 20); // 第3页，每页20条 -> offset=40, limit=20
            when(initiationMapper.countByFullText(eq(keyword), any())).thenReturn(100L);
            when(initiationMapper.searchByFullText(eq(keyword), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(buildVo(1L, "PRJ-001", "测试项目")));

            // When
            Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(100L);
            ArgumentCaptor<Integer> offsetCap = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
            verify(initiationMapper).searchByFullText(eq(keyword), any(), offsetCap.capture(), limitCap.capture());
            assertThat(offsetCap.getValue()).isEqualTo(40);
            assertThat(limitCap.getValue()).isEqualTo(20);
        }

        @Test
        @DisplayName("降级策略 - countByFullText 抛异常时返回空分页")
        void searchProjects_CountThrows_Degrade() {
            // Given
            String keyword = "园区";
            Pageable pageable = PageRequest.of(0, 10);
            when(initiationMapper.countByFullText(eq(keyword), any()))
                    .thenThrow(new RuntimeException("PG connection refused"));

            // When
            Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            verify(initiationMapper, never()).searchByFullText(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("降级策略 - searchByFullText 抛异常时返回空分页")
        void searchProjects_SearchThrows_Degrade() {
            // Given
            String keyword = "园区";
            Pageable pageable = PageRequest.of(0, 10);
            when(initiationMapper.countByFullText(eq(keyword), any())).thenReturn(5L);
            when(initiationMapper.searchByFullText(eq(keyword), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("tsvector syntax error"));

            // When
            Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("降级策略 - TenantContext 抛异常时返回空分页")
        void searchProjects_TenantContextThrows_Degrade() {
            // Given
            String keyword = "园区";
            Pageable pageable = PageRequest.of(0, 10);
            try (MockedStatic<TenantContext> mocked = Mockito.mockStatic(TenantContext.class, Mockito.CALLS_REAL_METHODS)) {
                mocked.when(TenantContext::getTenantId).thenThrow(new IllegalStateException("tenant not set"));

                // When
                Page<ProjectSearchVO> result = searchService.searchProjects(keyword, pageable);

                // Then
                assertThat(result.getContent()).isEmpty();
                assertThat(result.getTotalElements()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("reindexAll 方法")
    class ReindexAllTest {

        @Test
        @DisplayName("重建索引 - PG tsvector 无需重建，返回 no-op 标识")
        void reindexAll_NoOp() {
            // When
            String result = searchService.reindexAll();

            // Then
            assertThat(result).isEqualTo("pg-tsvector: no-op");
            // 重建不触发任何 Mapper 调用
            verify(initiationMapper, never()).countByFullText(any(), any());
            verify(initiationMapper, never()).searchByFullText(any(), any(), anyInt(), anyInt());
        }
    }

    /**
     * 构造测试用 ProjectSearchVO。
     */
    private ProjectSearchVO buildVo(Long id, String code, String name) {
        ProjectSearchVO vo = new ProjectSearchVO();
        vo.setId(id);
        vo.setProjectCode(code);
        vo.setProjectName(name);
        vo.setCustomerName("测试客户");
        vo.setContractName("测试合同");
        vo.setProjectType("OUTSOURCING");
        vo.setStage("PRE_INITIATION");
        vo.setPmName("张三");
        vo.setCreatedAt(LocalDateTime.of(2026, 7, 1, 9, 0, 0));
        vo.setUpdatedAt(LocalDateTime.of(2026, 7, 2, 10, 0, 0));
        return vo;
    }
}
