package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.LoginAuditDO;
import com.njydsz.pmis.system.mapper.LoginAuditMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAuditServiceImpl 单元测试")
class LoginAuditServiceImplTest {

    @Mock
    private LoginAuditMapper loginAuditMapper;

    @InjectMocks
    private LoginAuditServiceImpl loginAuditService;

    @Nested
    @DisplayName("record 方法")
    class RecordTest {

        @Test
        @DisplayName("记录登录审计应调用 mapper.insertLogin")
        void shouldRecordLoginAudit() {
            LoginAuditDO entity = new LoginAuditDO();
            entity.setUsername("admin");
            entity.setStatus("SUCCESS");
            when(loginAuditMapper.insertLogin(any(LoginAuditDO.class))).thenReturn(1);

            assertThatCode(() -> loginAuditService.record(entity)).doesNotThrowAnyException();
            verify(loginAuditMapper).insertLogin(entity);
        }
    }

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询应返回正确结果")
        void shouldReturnPagedAudits() {
            when(loginAuditMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<LoginAuditDO> result = loginAuditService.page(1, 10, "admin", "SUCCESS", null);

            assertThat(result).isNotNull();
            verify(loginAuditMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("不带过滤条件的分页查询也应正常返回")
        void shouldReturnPagedAuditsWithoutFilters() {
            when(loginAuditMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<LoginAuditDO> result = loginAuditService.page(1, 20, null, null, null);

            assertThat(result).isNotNull();
            verify(loginAuditMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("listByUsername 方法")
    class ListByUsernameTest {

        @Test
        @DisplayName("按用户名查询应返回审计列表")
        void shouldReturnAuditsByUsername() {
            LoginAuditDO audit = new LoginAuditDO();
            audit.setId(1L);
            audit.setUsername("admin");
            when(loginAuditMapper.selectByUsername(eq("admin"), anyInt())).thenReturn(List.of(audit));

            List<LoginAuditDO> result = loginAuditService.listByUsername("admin", 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("countByIp 方法")
    class CountByIpTest {

        @Test
        @DisplayName("统计 IP 登录次数应返回正确值")
        void shouldCountByIp() {
            when(loginAuditMapper.countByIpSince("192.168.1.1", "FAIL", 10)).thenReturn(5L);

            long count = loginAuditService.countByIp("192.168.1.1", "FAIL", 10);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("无失败记录时应返回 0")
        void shouldReturnZeroWhenNoFailures() {
            when(loginAuditMapper.countByIpSince("192.168.1.1", "FAIL", 10)).thenReturn(0L);

            long count = loginAuditService.countByIp("192.168.1.1", "FAIL", 10);

            assertThat(count).isEqualTo(0L);
        }
    }
}