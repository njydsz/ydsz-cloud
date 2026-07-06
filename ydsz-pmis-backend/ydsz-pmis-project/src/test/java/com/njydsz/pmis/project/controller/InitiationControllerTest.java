package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.literule.spi.BudgetSnapshotProvider;
import com.njydsz.pmis.literule.spi.BudgetSnapshotProvider.BudgetSnapshot;
import com.njydsz.pmis.project.service.InitiationService;
import com.njydsz.pmis.project.vo.BudgetSnapshotVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InitiationController} 单元测试
 *
 * <p>聚焦于批量预算快照端点 {@link InitiationController#batchBudgetSnapshots()} 的行为验证，
 * 覆盖正常返回、空列表、SPI 字段到 VO 的转换正确性。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InitiationController 单元测试")
class InitiationControllerTest {

    @Mock
    private InitiationService service;

    @Mock
    private BudgetSnapshotProvider budgetSnapshotProvider;

    @InjectMocks
    private InitiationController controller;

    @Nested
    @DisplayName("batchBudgetSnapshots 方法")
    class BatchBudgetSnapshotsTest {

        @Test
        @DisplayName("批量查询 - 正常返回多个快照，字段正确转换")
        void batchBudgetSnapshots_ReturnsMappedVOs() {
            // Given
            BudgetSnapshot s1 = new BudgetSnapshot(
                    "1001", "智慧园区项目",
                    new BigDecimal("1000000.00"), new BigDecimal("650000.00"), 0.65);
            BudgetSnapshot s2 = new BudgetSnapshot(
                    "1002", "数据中台建设",
                    new BigDecimal("800000.00"), new BigDecimal("960000.00"), 1.20);
            when(budgetSnapshotProvider.getBudgetSnapshots()).thenReturn(List.of(s1, s2));

            // When
            Result<List<BudgetSnapshotVO>> result = controller.batchBudgetSnapshots();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            List<BudgetSnapshotVO> data = result.getData();
            assertThat(data).hasSize(2);

            BudgetSnapshotVO vo1 = data.get(0);
            assertThat(vo1.getProjectId()).isEqualTo("1001");
            assertThat(vo1.getProjectName()).isEqualTo("智慧园区项目");
            assertThat(vo1.getTotalBudget()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            assertThat(vo1.getIncurredCost()).isEqualByComparingTo(new BigDecimal("650000.00"));
            assertThat(vo1.getUsageRatio()).isEqualTo(0.65);

            BudgetSnapshotVO vo2 = data.get(1);
            assertThat(vo2.getProjectId()).isEqualTo("1002");
            assertThat(vo2.getProjectName()).isEqualTo("数据中台建设");
            assertThat(vo2.getTotalBudget()).isEqualByComparingTo(new BigDecimal("800000.00"));
            assertThat(vo2.getIncurredCost()).isEqualByComparingTo(new BigDecimal("960000.00"));
            assertThat(vo2.getUsageRatio()).isEqualTo(1.20);

            verify(budgetSnapshotProvider).getBudgetSnapshots();
        }

        @Test
        @DisplayName("批量查询 - SPI 返回空列表，返回空结果")
        void batchBudgetSnapshots_EmptyList() {
            // Given
            when(budgetSnapshotProvider.getBudgetSnapshots()).thenReturn(Collections.emptyList());

            // When
            Result<List<BudgetSnapshotVO>> result = controller.batchBudgetSnapshots();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEmpty();
            verify(budgetSnapshotProvider).getBudgetSnapshots();
        }
    }

    @Nested
    @DisplayName("BudgetSnapshotVO.from 转换")
    class BudgetSnapshotVOFromTest {

        @Test
        @DisplayName("from - 入参为 null 返回 null")
        void from_NullReturnsNull() {
            assertThat(BudgetSnapshotVO.from(null)).isNull();
        }

        @Test
        @DisplayName("from - 字段一一映射")
        void from_FieldsMapped() {
            // Given
            BudgetSnapshot snapshot = new BudgetSnapshot(
                    "2001", "测试项目",
                    new BigDecimal("500000.00"), new BigDecimal("125000.00"), 0.25);

            // When
            BudgetSnapshotVO vo = BudgetSnapshotVO.from(snapshot);

            // Then
            assertThat(vo).isNotNull();
            assertThat(vo.getProjectId()).isEqualTo("2001");
            assertThat(vo.getProjectName()).isEqualTo("测试项目");
            assertThat(vo.getTotalBudget()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(vo.getIncurredCost()).isEqualByComparingTo(new BigDecimal("125000.00"));
            assertThat(vo.getUsageRatio()).isEqualTo(0.25);
        }
    }
}
