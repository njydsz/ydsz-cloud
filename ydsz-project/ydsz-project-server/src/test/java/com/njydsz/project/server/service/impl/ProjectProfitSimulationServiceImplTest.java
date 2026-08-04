package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;
import com.njydsz.project.domain.repository.project.IProjectProfitSimulationRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectProfitSimulationServiceImpl} 单元测试。
 *
 * <p>利润模拟 Service 是「财务经营分析 / 利润预测」业务域的核心 Service，
 * 维护利润模拟推演表，支撑 What-If 利润模拟 / 敏感性分析 / 定价测算。
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>CRUD 委托正确性（Service → Repository 的方法调用与参数传递）</li>
 *   <li>分页参数构造（Page 对象的 pageNum / pageSize 正确性）</li>
 *   <li>返回值传播（Repository 返回值原样透传）</li>
 *   <li>异常传播（Repository 异常不被吞掉）</li>
 *   <li>边界条件（null 入参、不存在记录、空分页）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("利润模拟服务 ProjectProfitSimulationServiceImpl 测试")
@ExtendWith(MockitoExtension.class)
class ProjectProfitSimulationServiceImplTest {

    @Mock
    private IProjectProfitSimulationRepository repository;

    @InjectMocks
    private ProjectProfitSimulationServiceImpl service;

    @Nested
    @DisplayName("getById — 根据主键查询利润模拟")
    class GetByIdTest {

        @Test
        @DisplayName("记录存在时，应返回 Repository 查询结果")
        void getById_shouldReturnEntity_whenExists() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            when(repository.getById("sim-001")).thenReturn(simulation);

            // when
            ProjectProfitSimulation result = service.getById("sim-001");

            // then
            assertThat(result).isSameAs(simulation);
            verify(repository).getById("sim-001");
        }

        @Test
        @DisplayName("记录不存在时，应返回 null")
        void getById_shouldReturnNull_whenNotExists() {
            // given
            when(repository.getById("nonexistent")).thenReturn(null);

            // when
            ProjectProfitSimulation result = service.getById("nonexistent");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Repository 抛异常时，异常应原样传播")
        void getById_shouldPropagateException_whenRepositoryThrows() {
            // given
            RuntimeException dbError = new RuntimeException("DB connection lost");
            when(repository.getById("sim-001")).thenThrow(dbError);

            // when & then
            assertThatThrownBy(() -> service.getById("sim-001"))
                    .isSameAs(dbError);
        }
    }

    @Nested
    @DisplayName("page — 分页查询利润模拟")
    class PageTest {

        @Test
        @DisplayName("应使用正确的 pageNum / pageSize 构造 Page 并委托 Repository")
        void page_shouldDelegateWithCorrectPageParams() {
            // given
            Page<ProjectProfitSimulation> expectedPage = new Page<>(2, 20);
            expectedPage.setTotal(100);
            when(repository.page(any(Page.class))).thenReturn(expectedPage);

            // when
            IPage<ProjectProfitSimulation> result = service.page(2, 20);

            // then
            ArgumentCaptor<Page<ProjectProfitSimulation>> captor =
                    ArgumentCaptor.forClass(Page.class);
            verify(repository).page(captor.capture());
            Page<ProjectProfitSimulation> captured = captor.getValue();
            assertThat(captured.getCurrent()).isEqualTo(2L);
            assertThat(captured.getSize()).isEqualTo(20L);
            assertThat(result).isSameAs(expectedPage);
            assertThat(result.getTotal()).isEqualTo(100L);
        }

        @Test
        @DisplayName("第一页查询时，pageNum 应为 1")
        void page_shouldHandleFirstPage() {
            // given
            Page<ProjectProfitSimulation> emptyPage = new Page<>(1, 10);
            when(repository.page(any(Page.class))).thenReturn(emptyPage);

            // when
            IPage<ProjectProfitSimulation> result = service.page(1, 10);

            // then
            ArgumentCaptor<Page<ProjectProfitSimulation>> captor =
                    ArgumentCaptor.forClass(Page.class);
            verify(repository).page(captor.capture());
            assertThat(captor.getValue().getCurrent()).isEqualTo(1L);
            assertThat(result).isSameAs(emptyPage);
        }

        @Test
        @DisplayName("每页条数为 0 时，仍应委托 Repository（边界值由 DB 层校验）")
        void page_shouldDelegate_whenPageSizeIsZero() {
            // given
            Page<ProjectProfitSimulation> page = new Page<>(1, 0);
            when(repository.page(any(Page.class))).thenReturn(page);

            // when
            IPage<ProjectProfitSimulation> result = service.page(1, 0);

            // then
            assertThat(result).isSameAs(page);
            verify(repository).page(any(Page.class));
        }
    }

    @Nested
    @DisplayName("save — 新增利润模拟")
    class SaveTest {

        @Test
        @DisplayName("Repository 保存成功时，应返回 true")
        void save_shouldReturnTrue_whenRepositorySucceeds() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            when(repository.save(simulation)).thenReturn(true);

            // when
            boolean result = service.save(simulation);

            // then
            assertThat(result).isTrue();
            verify(repository).save(simulation);
        }

        @Test
        @DisplayName("Repository 保存失败时，应返回 false")
        void save_shouldReturnFalse_whenRepositoryFails() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            when(repository.save(simulation)).thenReturn(false);

            // when
            boolean result = service.save(simulation);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应将同一实体实例原样传递给 Repository（不做字段篡改）")
        void save_shouldPassSameEntityInstance() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            when(repository.save(any(ProjectProfitSimulation.class))).thenReturn(true);

            // when
            service.save(simulation);

            // then
            ArgumentCaptor<ProjectProfitSimulation> captor =
                    ArgumentCaptor.forClass(ProjectProfitSimulation.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(simulation);
        }

        @Test
        @DisplayName("Repository 抛异常时，异常应原样传播（事务回滚由 @Transactional 保证）")
        void save_shouldPropagateException_whenRepositoryThrows() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            RuntimeException dbError = new RuntimeException("Duplicate key");
            when(repository.save(simulation)).thenThrow(dbError);

            // when & then
            assertThatThrownBy(() -> service.save(simulation))
                    .isSameAs(dbError);
        }
    }

    @Nested
    @DisplayName("updateById — 更新利润模拟")
    class UpdateByIdTest {

        @Test
        @DisplayName("Repository 更新成功时，应返回 true")
        void updateById_shouldReturnTrue_whenRepositorySucceeds() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            when(repository.updateById(simulation)).thenReturn(true);

            // when
            boolean result = service.updateById(simulation);

            // then
            assertThat(result).isTrue();
            verify(repository).updateById(simulation);
        }

        @Test
        @DisplayName("Repository 更新失败（乐观锁冲突 / 记录不存在）时，应返回 false")
        void updateById_shouldReturnFalse_whenRepositoryFails() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            when(repository.updateById(simulation)).thenReturn(false);

            // when
            boolean result = service.updateById(simulation);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Repository 抛异常时，异常应原样传播")
        void updateById_shouldPropagateException_whenRepositoryThrows() {
            // given
            ProjectProfitSimulation simulation = new ProjectProfitSimulation();
            RuntimeException dbError = new RuntimeException("Optimistic lock conflict");
            when(repository.updateById(simulation)).thenThrow(dbError);

            // when & then
            assertThatThrownBy(() -> service.updateById(simulation))
                    .isSameAs(dbError);
        }
    }

    @Nested
    @DisplayName("removeById — 逻辑删除利润模拟")
    class RemoveByIdTest {

        @Test
        @DisplayName("Repository 删除成功时，应返回 true")
        void removeById_shouldReturnTrue_whenRepositorySucceeds() {
            // given
            when(repository.removeById("sim-001")).thenReturn(true);

            // when
            boolean result = service.removeById("sim-001");

            // then
            assertThat(result).isTrue();
            verify(repository).removeById("sim-001");
        }

        @Test
        @DisplayName("Repository 删除失败时，应返回 false")
        void removeById_shouldReturnFalse_whenRepositoryFails() {
            // given
            when(repository.removeById("nonexistent")).thenReturn(false);

            // when
            boolean result = service.removeById("nonexistent");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Repository 抛异常时，异常应原样传播")
        void removeById_shouldPropagateException_whenRepositoryThrows() {
            // given
            RuntimeException dbError = new RuntimeException("Foreign key violation");
            when(repository.removeById("sim-001")).thenThrow(dbError);

            // when & then
            assertThatThrownBy(() -> service.removeById("sim-001"))
                    .isSameAs(dbError);
        }

        @Test
        @DisplayName("传入 null id 时，应将 null 原样传递给 Repository")
        void removeById_shouldPassNullIdToRepository() {
            // given
            when(repository.removeById((String) null)).thenReturn(false);

            // when
            boolean result = service.removeById(null);

            // then
            assertThat(result).isFalse();
            verify(repository).removeById((String) null);
        }
    }

    @Nested
    @DisplayName("方法间独立性 — 各方法不应产生意外调用")
    class IsolationTest {

        @Test
        @DisplayName("getById 不应触发 save / updateById / removeById")
        void getById_shouldNotTriggerWriteOperations() {
            // given
            when(repository.getById("sim-001")).thenReturn(null);

            // when
            service.getById("sim-001");

            // then
            verify(repository, never()).save(any());
            verify(repository, never()).updateById(any());
            verify(repository, never()).removeById(any());
        }
    }
}
