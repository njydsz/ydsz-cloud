package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.dto.RouteRuleUpsertDTO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.mapper.MsgRouteRuleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路由规则服务单元测试。
 *
 * <p>覆盖 SpEL 条件匹配、Redis 缓存读取/回填/失效、CRUD、缓存未命中回退 DB。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("路由规则服务 RouteRuleServiceImpl 单元测试")
class RouteRuleServiceImplTest {

    @Mock
    private MsgRouteRuleMapper msgRouteRuleMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 使用真实 SpEL 解析器，验证条件表达式求值 */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @InjectMocks
    private RouteRuleServiceImpl routeRuleService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("1");
        // 注入真实 SpEL 解析器
        routeRuleService = new RouteRuleServiceImpl(msgRouteRuleMapper, expressionParser, stringRedisTemplate);
        // opsForValue 必须返回 mock，否则 loadEnabledRulesFromCache 中 opsForValue().get() 会 NPE
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== match (SpEL) ====================

    @Test
    @DisplayName("正常场景：SpEL 条件命中返回规则")
    void spel条件命中返回规则() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setConditionExpr("#request.channel == 'SMS'");
        rule.setTargetChannel("SMS");
        // 缓存未命中，回退 DB
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNotNull(result);
        assertEquals("r1", result.getId());
    }

    @Test
    @DisplayName("正常场景：SpEL 条件未命中继续遍历")
    void spel条件未命中继续遍历() {
        MsgRouteRuleDO rule1 = new MsgRouteRuleDO();
        rule1.setId("r1");
        rule1.setConditionExpr("#request.channel == 'EMAIL'");
        rule1.setTargetChannel("EMAIL");
        MsgRouteRuleDO rule2 = new MsgRouteRuleDO();
        rule2.setId("r2");
        rule2.setConditionExpr("#request.channel == 'SMS'");
        rule2.setTargetChannel("SMS");
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule1, rule2));

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNotNull(result);
        assertEquals("r2", result.getId());
    }

    @Test
    @DisplayName("正常场景：无条件表达式视为恒真命中")
    void 无条件表达式恒真命中() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setConditionExpr(null);
        rule.setTargetChannel("SMS");
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNotNull(result);
        assertEquals("r1", result.getId());
    }

    @Test
    @DisplayName("异常场景：SpEL 求值失败时跳过该规则")
    void spel求值失败跳过规则() {
        MsgRouteRuleDO badRule = new MsgRouteRuleDO();
        badRule.setId("r1");
        badRule.setConditionExpr("#request.invalid.method()");
        badRule.setTargetChannel("SMS");
        MsgRouteRuleDO goodRule = new MsgRouteRuleDO();
        goodRule.setId("r2");
        goodRule.setConditionExpr("#request.channel == 'SMS'");
        goodRule.setTargetChannel("SMS");
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(badRule, goodRule));

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNotNull(result);
        assertEquals("r2", result.getId());
    }

    @Test
    @DisplayName("边界场景：request 为 null 返回 null")
    void request为null返回null() {
        MsgRouteRuleDO result = routeRuleService.match(null);
        assertNull(result);
    }

    @Test
    @DisplayName("边界场景：无启用的规则时返回 null")
    void 无启用规则返回null() {
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNull(result);
    }

    // ==================== 缓存测试 ====================

    @Test
    @DisplayName("缓存场景：缓存命中时直接返回，不查 DB")
    void 缓存命中不查DB() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setConditionExpr("#request.channel == 'SMS'");
        String cachedJson = "[{\"id\":\"r1\",\"conditionExpr\":\"#request.channel == 'SMS'\",\"status\":\"ENABLED\"}]";
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(cachedJson);

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNotNull(result);
        assertEquals("r1", result.getId());
        verify(msgRouteRuleMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("缓存场景：缓存未命中时查 DB 并回填缓存")
    void 缓存未命中查DB并回填() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setConditionExpr("#request.channel == 'SMS'");
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        routeRuleService.match(request);

        // 验证缓存回填
        verify(valueOperations).set(eq(MessageConstants.ROUTE_RULE_CACHE_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("异常场景：缓存读取异常时回退 DB")
    void 缓存读取异常回退DB() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setConditionExpr("#request.channel == 'SMS'");
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenThrow(new RuntimeException("Redis 异常"));
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");

        MsgRouteRuleDO result = routeRuleService.match(request);

        assertNotNull(result);
    }

    // ==================== create ====================

    @Test
    @DisplayName("正常场景：创建路由规则")
    void 创建路由规则() {
        RouteRuleUpsertDTO dto = new RouteRuleUpsertDTO();
        dto.setRuleCode("RULE_001");
        dto.setRuleName("SMS 路由");
        dto.setConditionExpr("#request.channel == 'SMS'");
        dto.setTargetChannel("SMS");
        when(msgRouteRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgRouteRuleDO result = routeRuleService.create(dto);

        assertNotNull(result);
        assertEquals("RULE_001", result.getRuleCode());
        assertEquals("ENABLED", result.getStatus());
        verify(msgRouteRuleMapper).insert(result);
        // 创建后失效缓存
        verify(stringRedisTemplate).delete(MessageConstants.ROUTE_RULE_CACHE_KEY);
    }

    @Test
    @DisplayName("边界场景：priority 为 null 时默认 100")
    void priority为null默认100() {
        RouteRuleUpsertDTO dto = new RouteRuleUpsertDTO();
        dto.setRuleCode("RULE_001");
        when(msgRouteRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgRouteRuleDO result = routeRuleService.create(dto);

        assertEquals(100, result.getPriority());
    }

    @Test
    @DisplayName("异常场景：dto 为空抛 BizException")
    void createDto为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> routeRuleService.create(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：ruleCode 为空抛 BizException")
    void ruleCode为空抛异常() {
        RouteRuleUpsertDTO dto = new RouteRuleUpsertDTO();

        BizException ex = assertThrows(BizException.class, () -> routeRuleService.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：规则编码已存在抛 DUPLICATE_KEY")
    void 规则编码已存在抛异常() {
        RouteRuleUpsertDTO dto = new RouteRuleUpsertDTO();
        dto.setRuleCode("RULE_001");
        MsgRouteRuleDO existing = new MsgRouteRuleDO();
        when(msgRouteRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> routeRuleService.create(dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(msgRouteRuleMapper, never()).insert(any(MsgRouteRuleDO.class));
    }

    // ==================== getById / update / delete ====================

    @Test
    @DisplayName("正常场景：按 ID 查询规则")
    void 按ID查询规则() {
        MsgRouteRuleDO entity = new MsgRouteRuleDO();
        entity.setId("1");
        entity.setRuleCode("RULE_001");
        when(msgRouteRuleMapper.selectById("1")).thenReturn(entity);

        MsgRouteRuleDO result = routeRuleService.getById("1");

        assertEquals("RULE_001", result.getRuleCode());
    }

    @Test
    @DisplayName("异常场景：ID 为空抛 BizException")
    void getByIdId为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> routeRuleService.getById(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：规则不存在抛 NOT_FOUND")
    void 规则不存在抛异常() {
        when(msgRouteRuleMapper.selectById("999")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> routeRuleService.getById("999"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常场景：更新规则并失效缓存")
    void 更新规则并失效缓存() {
        MsgRouteRuleDO entity = new MsgRouteRuleDO();
        entity.setId("1");
        entity.setRuleCode("RULE_001");
        when(msgRouteRuleMapper.selectById("1")).thenReturn(entity);

        RouteRuleUpsertDTO dto = new RouteRuleUpsertDTO();
        dto.setRuleName("新名称");
        dto.setTargetChannel("EMAIL");

        MsgRouteRuleDO result = routeRuleService.update("1", dto);

        assertEquals("新名称", result.getRuleName());
        assertEquals("EMAIL", result.getTargetChannel());
        verify(msgRouteRuleMapper).updateById(entity);
        verify(stringRedisTemplate).delete(MessageConstants.ROUTE_RULE_CACHE_KEY);
    }

    @Test
    @DisplayName("异常场景：更新时 ID 为空抛 BizException")
    void 更新时id为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> routeRuleService.update(null, new RouteRuleUpsertDTO()));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常场景：删除规则并失效缓存")
    void 删除规则并失效缓存() {
        routeRuleService.delete("1");
        verify(msgRouteRuleMapper).deleteById("1");
        verify(stringRedisTemplate).delete(MessageConstants.ROUTE_RULE_CACHE_KEY);
    }

    @Test
    @DisplayName("异常场景：删除时 ID 为空抛 BizException")
    void 删除时id为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> routeRuleService.delete(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== page / listEnabled ====================

    @Test
    @DisplayName("正常场景：分页查询规则")
    void 分页查询规则() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        Page<MsgRouteRuleDO> mockPage = new Page<>();
        when(msgRouteRuleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgRouteRuleDO> result = routeRuleService.page(query);

        assertNotNull(result);
    }

    @Test
    @DisplayName("正常场景：listEnabled 通过缓存加载")
    void listEnabled通过缓存加载() {
        String cachedJson = "[{\"id\":\"r1\",\"status\":\"ENABLED\"}]";
        when(stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY)).thenReturn(cachedJson);

        List<MsgRouteRuleDO> result = routeRuleService.listEnabled();

        assertEquals(1, result.size());
    }
}
