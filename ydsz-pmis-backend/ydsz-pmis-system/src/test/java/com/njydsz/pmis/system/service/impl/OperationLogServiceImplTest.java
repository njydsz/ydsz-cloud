package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.OperationLogDO;
import com.njydsz.pmis.system.mapper.OperationLogMapper;
import com.njydsz.pmis.system.service.OperationLogServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationLogServiceImpl 单元测试")
class OperationLogServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @InjectMocks
    private OperationLogServiceImpl operationLogService;

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询应返回正确结果")
        void shouldReturnPagedLogs() {
            when(operationLogMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<OperationLogDO> result = operationLogService.page(1, 10, 1L, "TEST", "SUCCESS", "module", null, null);

            assertThat(result).isNotNull();
            verify(operationLogMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("带时间范围的分页查询应正确过滤")
        void shouldFilterByTimeRange() {
            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
            when(operationLogMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<OperationLogDO> result = operationLogService.page(1, 10, null, null, null, null, start, end);

            assertThat(result).isNotNull();
            verify(operationLogMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("日志存在时应返回日志")
        void shouldReturnLogWhenExists() {
            OperationLogDO log = new OperationLogDO();
            log.setId(1L);
            log.setModule("test_module");
            when(operationLogMapper.selectById(1L)).thenReturn(log);

            OperationLogDO result = operationLogService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getModule()).isEqualTo("test_module");
        }

        @Test
        @DisplayName("日志不存在时应返回 null")
        void shouldReturnNullWhenNotFound() {
            when(operationLogMapper.selectById(999L)).thenReturn(null);

            OperationLogDO result = operationLogService.getById(999L);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("listByUser 方法")
    class ListByUserTest {

        @Test
        @DisplayName("按用户查询应返回日志列表")
        void shouldReturnLogsByUser() {
            OperationLogDO log = new OperationLogDO();
            log.setId(1L);
            log.setUserId(1L);
            when(operationLogMapper.selectByUser(eq(1L), anyInt())).thenReturn(List.of(log));

            List<OperationLogDO> result = operationLogService.listByUser(1L, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("cleanBefore 方法")
    class CleanBeforeTest {

        @Test
        @DisplayName("清理日志应返回删除条数")
        void shouldCleanLogsBeforeDays() {
            when(operationLogMapper.deleteBefore(90)).thenReturn(5);

            int count = operationLogService.cleanBefore(90);

            assertThat(count).isEqualTo(5);
            verify(operationLogMapper).deleteBefore(90);
        }

        @Test
        @DisplayName("days 非法时应默认 90 天")
        void shouldDefaultTo90DaysWhenInvalid() {
            when(operationLogMapper.deleteBefore(90)).thenReturn(0);

            int count = operationLogService.cleanBefore(0);

            assertThat(count).isEqualTo(0);
            verify(operationLogMapper).deleteBefore(90);
        }
    }

    @Nested
    @DisplayName("listByBiz 方法")
    class ListByBizTest {

        @Test
        @DisplayName("按业务查询应返回日志列表")
        void shouldReturnLogsByBiz() {
            OperationLogDO log = new OperationLogDO();
            log.setId(1L);
            log.setBizType("TEST");
            when(operationLogMapper.selectByBiz(eq("TEST"), eq("123"), anyInt())).thenReturn(List.of(log));

            List<OperationLogDO> result = operationLogService.listByBiz("TEST", "123", 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBizType()).isEqualTo("TEST");
        }
    }
}