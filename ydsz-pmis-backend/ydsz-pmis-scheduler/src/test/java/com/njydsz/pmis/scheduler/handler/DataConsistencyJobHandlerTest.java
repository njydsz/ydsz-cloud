package com.njydsz.pmis.scheduler.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DataConsistencyJobHandler 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DataConsistencyJobHandlerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DataConsistencyJobHandler handler;

    @Test
    void execute_shouldReturnZeroIssuesWhenNoInconsistency() throws Exception {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        Object result = handler.execute(null);
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("issues")).isEqualTo(0);
    }

    @Test
    void execute_shouldCountInconsistencies() throws Exception {
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(Map.of("initiation_id", 1)))
                .thenReturn(List.of())
                .thenReturn(List.of());
        Object result = handler.execute(null);
        assertThat(((Map<?, ?>) result).get("issues")).isEqualTo(1);
    }

    @Test
    void execute_shouldNotThrowWhenSqlFails() throws Exception {
        when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("SQL error"));
        Object result = handler.execute(null); // should not throw
        assertThat(((Map<?, ?>) result).get("issues")).isEqualTo(0);
    }
}
