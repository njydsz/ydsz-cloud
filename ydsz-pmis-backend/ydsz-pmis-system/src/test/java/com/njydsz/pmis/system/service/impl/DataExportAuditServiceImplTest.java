package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.DataExportAuditDO;
import com.njydsz.pmis.system.mapper.DataExportAuditMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataExportAuditServiceImpl 单元测试")
class DataExportAuditServiceImplTest {

    @Mock
    private DataExportAuditMapper mapper;

    @InjectMocks
    private DataExportAuditServiceImpl dataExportAuditService;

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("分页查询应返回正确结果")
        void shouldReturnPagedResults() {
            when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<DataExportAuditDO> result = dataExportAuditService.page(1, 10, 1L, null);

            assertThat(result).isNotNull();
            verify(mapper).selectPage(any(Page.class), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("按模块分页查询应正确过滤")
        void shouldFilterByModule() {
            when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<DataExportAuditDO> result = dataExportAuditService.page(1, 10, null, "report");

            assertThat(result).isNotNull();
            verify(mapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("listByUser 方法")
    class ListByUserTest {

        @Test
        @DisplayName("按用户查询应返回导出历史")
        void shouldReturnExportsByUser() {
            DataExportAuditDO audit = new DataExportAuditDO();
            audit.setId(1L);
            audit.setUserId(1L);
            audit.setExportModule("report");
            when(mapper.selectByUser(eq(1L), anyInt())).thenReturn(List.of(audit));

            List<DataExportAuditDO> result = dataExportAuditService.listByUser(1L, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getExportModule()).isEqualTo("report");
        }

        @Test
        @DisplayName("用户无导出记录时应返回空列表")
        void shouldReturnEmptyWhenNoExports() {
            when(mapper.selectByUser(eq(999L), anyInt())).thenReturn(List.of());

            List<DataExportAuditDO> result = dataExportAuditService.listByUser(999L, 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("记录存在时应返回实体")
        void shouldReturnEntityWhenExists() {
            DataExportAuditDO audit = new DataExportAuditDO();
            audit.setId(1L);
            audit.setExportModule("report");
            when(mapper.selectById(1L)).thenReturn(audit);

            DataExportAuditDO result = dataExportAuditService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getExportModule()).isEqualTo("report");
        }

        @Test
        @DisplayName("记录不存在时应返回 null")
        void shouldReturnNullWhenNotFound() {
            when(mapper.selectById(999L)).thenReturn(null);

            DataExportAuditDO result = dataExportAuditService.getById(999L);

            assertThat(result).isNull();
        }
    }
}