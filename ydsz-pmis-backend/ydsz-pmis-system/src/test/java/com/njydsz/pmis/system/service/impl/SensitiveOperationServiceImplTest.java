package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.SensitiveOperationDO;
import com.njydsz.pmis.system.mapper.SensitiveOperationMapper;
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
@DisplayName("SensitiveOperationServiceImpl 单元测试")
class SensitiveOperationServiceImplTest {

    @Mock
    private SensitiveOperationMapper mapper;

    @InjectMocks
    private SensitiveOperationServiceImpl sensitiveOpService;

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询应返回正确结果")
        void shouldReturnPagedResults() {
            when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<SensitiveOperationDO> result = sensitiveOpService.page(1, 10, 1L, null);

            assertThat(result).isNotNull();
            verify(mapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("按操作类型分页查询应正确过滤")
        void shouldFilterByOpType() {
            when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<SensitiveOperationDO> result = sensitiveOpService.page(1, 10, null, "DELETE");

            assertThat(result).isNotNull();
            verify(mapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("listByUser 方法")
    class ListByUserTest {

        @Test
        @DisplayName("按用户查询应返回操作列表")
        void shouldReturnOpsByUser() {
            SensitiveOperationDO op = new SensitiveOperationDO();
            op.setId(1L);
            op.setUserId(1L);
            op.setOperationCode("DELETE_PROJECT");
            when(mapper.selectByUser(eq(1L), anyInt())).thenReturn(List.of(op));

            List<SensitiveOperationDO> result = sensitiveOpService.listByUser(1L, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOperationCode()).isEqualTo("DELETE_PROJECT");
        }

        @Test
        @DisplayName("用户无操作记录时应返回空列表")
        void shouldReturnEmptyWhenNoOperations() {
            when(mapper.selectByUser(eq(999L), anyInt())).thenReturn(List.of());

            List<SensitiveOperationDO> result = sensitiveOpService.listByUser(999L, 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("记录存在时应返回实体")
        void shouldReturnEntityWhenExists() {
            SensitiveOperationDO op = new SensitiveOperationDO();
            op.setId(1L);
            op.setOperationCode("DELETE_PROJECT");
            when(mapper.selectById(1L)).thenReturn(op);

            SensitiveOperationDO result = sensitiveOpService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getOperationCode()).isEqualTo("DELETE_PROJECT");
        }

        @Test
        @DisplayName("记录不存在时应返回 null")
        void shouldReturnNullWhenNotFound() {
            when(mapper.selectById(999L)).thenReturn(null);

            SensitiveOperationDO result = sensitiveOpService.getById(999L);

            assertThat(result).isNull();
        }
    }
}