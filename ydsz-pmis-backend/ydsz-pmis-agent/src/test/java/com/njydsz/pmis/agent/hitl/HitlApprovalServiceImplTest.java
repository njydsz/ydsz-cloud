package com.njydsz.pmis.agent.hitl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.react.ReActLoop;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.entity.HitlApprovalRequestDO;
import com.njydsz.pmis.agent.enums.HitlApprovalStatus;
import com.njydsz.pmis.agent.mapper.HitlApprovalRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HitlApprovalServiceImpl 单元测试（P3-4 落地）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HITL 审批服务测试")
class HitlApprovalServiceImplTest {

    @Mock
    private ObjectProvider<HitlApprovalRequestMapper> mapperProvider;
    @Mock
    private ObjectProvider<ReActLoop> reactLoopProvider;
    @Mock
    private HitlApprovalRequestMapper mapper;
    @Mock
    private ReActLoop reactLoop;

    private HitlApprovalServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        when(reactLoopProvider.getIfAvailable()).thenReturn(reactLoop);
        service = new HitlApprovalServiceImpl(mapperProvider, reactLoopProvider, objectMapper);
    }

    private ReActSnapshot buildSnapshot() {
        AgentContext ctx = new AgentContext();
        ctx.setTraceId("trace-1");
        return ReActSnapshot.of("sys", "user", "orig", List.of(),
                ctx, 5, 1, "thought", "sensitive_tool", Map.of("key", "val"));
    }

    private HitlApprovalRequestDO buildPendingEntity(String id) {
        HitlApprovalRequestDO entity = new HitlApprovalRequestDO();
        entity.setId(id);
        entity.setStatus(HitlApprovalStatus.PENDING.getCode());
        entity.setToolName("sensitive_tool");
        try {
            entity.setSnapshotJson(objectMapper.writeValueAsString(buildSnapshot()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entity;
    }

    @Nested
    @DisplayName("createRequest")
    class CreateRequestTest {

        @Test
        @DisplayName("正常创建审批请求")
        void shouldCreateRequest() {
            ReActSnapshot snapshot = buildSnapshot();

            HitlApprovalRequestDO result = service.createRequest(snapshot,
                    "RISK_WARNING", "PROJECT", "proj-1", "项目A",
                    "trace-1", "user-1", "张三", 30);

            assertThat(result).isNotNull();
            assertThat(result.getAgentType()).isEqualTo("RISK_WARNING");
            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(result.getToolName()).isEqualTo("sensitive_tool");
            assertThat(result.getTimeoutAt()).isNotNull();
            verify(mapper).insert(any(HitlApprovalRequestDO.class));
        }

        @Test
        @DisplayName("timeoutMinutes=0 时不设超时时间")
        void shouldNotSetTimeoutWhenZero() {
            ReActSnapshot snapshot = buildSnapshot();

            HitlApprovalRequestDO result = service.createRequest(snapshot,
                    "RISK_WARNING", null, null, null, "trace-1", null, null, 0);

            assertThat(result.getTimeoutAt()).isNull();
        }

        @Test
        @DisplayName("Mapper 不可用时抛异常")
        void shouldThrowWhenMapperUnavailable() {
            when(mapperProvider.getIfAvailable()).thenReturn(null);
            ReActSnapshot snapshot = buildSnapshot();

            assertThatThrownBy(() -> service.createRequest(snapshot,
                    "RISK_WARNING", null, null, null, "trace-1", null, null, 30))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Mapper 不可用");
        }
    }

    @Nested
    @DisplayName("approve")
    class ApproveTest {

        @Test
        @DisplayName("正常批准并恢复循环")
        void shouldApproveAndResume() {
            HitlApprovalRequestDO entity = buildPendingEntity("appr-1");
            when(mapper.selectById("appr-1")).thenReturn(entity);
            ReActResult expected = ReActResult.success("final", List.of());
            when(reactLoop.resume(any(ReActSnapshot.class))).thenReturn(expected);

            ReActResult result = service.approve("appr-1", "user-2", "李四", "同意");

            assertThat(result.isSuccess()).isTrue();
            ArgumentCaptor<HitlApprovalRequestDO> captor = ArgumentCaptor.forClass(HitlApprovalRequestDO.class);
            verify(mapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
            assertThat(captor.getValue().getApproverName()).isEqualTo("李四");
            assertThat(captor.getValue().getApproverComment()).isEqualTo("同意");
            assertThat(captor.getValue().getResolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("请求不存在时抛异常")
        void shouldThrowWhenNotFound() {
            when(mapper.selectById("not-exist")).thenReturn(null);

            assertThatThrownBy(() -> service.approve("not-exist", "u", "n", "c"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不存在");
        }

        @Test
        @DisplayName("已批准的请求不可再次批准")
        void shouldThrowWhenAlreadyApproved() {
            HitlApprovalRequestDO entity = buildPendingEntity("appr-1");
            entity.setStatus(HitlApprovalStatus.APPROVED.getCode());
            when(mapper.selectById("appr-1")).thenReturn(entity);

            assertThatThrownBy(() -> service.approve("appr-1", "u", "n", "c"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不允许");
        }
    }

    @Nested
    @DisplayName("reject")
    class RejectTest {

        @Test
        @DisplayName("正常拒绝并恢复循环")
        void shouldRejectAndResume() {
            HitlApprovalRequestDO entity = buildPendingEntity("appr-1");
            when(mapper.selectById("appr-1")).thenReturn(entity);
            ReActResult expected = ReActResult.failure("rejected", List.of());
            when(reactLoop.resume(any(ReActSnapshot.class))).thenReturn(expected);

            ReActResult result = service.reject("appr-1", "user-2", "李四", "参数不对");

            assertThat(result.isSuccess()).isFalse();
            verify(mapper).updateById(any(HitlApprovalRequestDO.class));
            verify(reactLoop).resume(any(ReActSnapshot.class));
        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelTest {

        @Test
        @DisplayName("正常取消不恢复循环")
        void shouldCancelWithoutResume() {
            HitlApprovalRequestDO entity = buildPendingEntity("appr-1");
            when(mapper.selectById("appr-1")).thenReturn(entity);

            service.cancel("appr-1", "user-2", "李四", "手动取消");

            verify(mapper).updateById(any(HitlApprovalRequestDO.class));
            verify(reactLoop, never()).resume(any());
        }
    }

    @Nested
    @DisplayName("timeoutExpired")
    class TimeoutTest {

        @Test
        @DisplayName("超时的 PENDING 请求被标记为 TIMEOUT")
        void shouldTimeoutPendingRequests() {
            HitlApprovalRequestDO expired = buildPendingEntity("appr-1");
            expired.setTimeoutAt(LocalDateTime.now().minusMinutes(10));
            when(mapper.selectList(any())).thenReturn(List.of(expired));

            int count = service.timeoutExpired();

            assertThat(count).isEqualTo(1);
            verify(mapper).updateById(any(HitlApprovalRequestDO.class));
        }

        @Test
        @DisplayName("无超时请求时返回 0")
        void shouldReturnZeroWhenNoExpired() {
            when(mapper.selectList(any())).thenReturn(List.of());

            int count = service.timeoutExpired();

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Mapper 不可用时返回 0")
        void shouldReturnZeroWhenMapperUnavailable() {
            when(mapperProvider.getIfAvailable()).thenReturn(null);

            int count = service.timeoutExpired();

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("查询")
    class QueryTest {

        @Test
        @DisplayName("getById 正常返回")
        void shouldGetById() {
            HitlApprovalRequestDO entity = buildPendingEntity("appr-1");
            when(mapper.selectById("appr-1")).thenReturn(entity);

            HitlApprovalRequestDO result = service.getById("appr-1");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("appr-1");
        }

        @Test
        @DisplayName("page 正常分页")
        void shouldPage() {
            Page<HitlApprovalRequestDO> page = new Page<>();
            page.setRecords(List.of(buildPendingEntity("appr-1")));
            page.setTotal(1);
            when(mapper.selectPage(any(Page.class), any())).thenReturn(page);

            Page<HitlApprovalRequestDO> result = service.page(1, 10, "PENDING", null, null, null);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1L);
        }

        @Test
        @DisplayName("listPending 正常返回")
        void shouldListPending() {
            when(mapper.selectList(any())).thenReturn(List.of(buildPendingEntity("appr-1")));

            List<HitlApprovalRequestDO> result = service.listPending(20);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Mapper 不可用时返回空")
        void shouldReturnEmptyWhenMapperUnavailable() {
            when(mapperProvider.getIfAvailable()).thenReturn(null);

            assertThat(service.getById("x")).isNull();
            assertThat(service.listPending(10)).isEmpty();
            assertThat(service.page(1, 10, null, null, null, null).getRecords()).isEmpty();
        }
    }
}
