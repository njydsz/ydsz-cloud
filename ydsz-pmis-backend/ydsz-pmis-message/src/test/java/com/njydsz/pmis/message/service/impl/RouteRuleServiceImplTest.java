package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.mapper.MsgRouteRuleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link RouteRuleServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RouteRuleServiceImpl 路由规则测试")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RouteRuleServiceImplTest {

    @Mock
    private MsgRouteRuleMapper msgRouteRuleMapper;

    private final ExpressionParser parser = new SpelExpressionParser();

    @InjectMocks
    private RouteRuleServiceImpl routeRuleService;

    RouteRuleServiceImplTest() {
        // 通过反射注入真实 parser,绕过 Mockito 对 final 字段限制
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 手动注入 SpEL 解析器
        try {
            java.lang.reflect.Field f = RouteRuleServiceImpl.class.getDeclaredField("expressionParser");
            f.setAccessible(true);
            f.set(routeRuleService, parser);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("match SpEL 命中时返回规则")
    void matchShouldReturnWhenExpressionTrue() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setPriority(1);
        rule.setConditionExpr("#request.channel == 'SMS'");
        rule.setTargetChannel("EMAIL");
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        MsgRouteRuleDO matched = routeRuleService.match(req);

        assertNotNull(matched);
        assertEquals("r1", matched.getId());
    }

    @Test
    @DisplayName("match SpEL 未命中返回 null")
    void matchShouldReturnNullWhenExpressionFalse() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setPriority(1);
        rule.setConditionExpr("#request.channel == 'EMAIL'");
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        MsgRouteRuleDO matched = routeRuleService.match(req);

        assertNull(matched);
    }

    @Test
    @DisplayName("match SpEL 求值失败跳过该规则")
    void matchShouldSkipRuleWhenSpelFails() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setPriority(1);
        rule.setConditionExpr("#request.invalid..method");
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        MsgRouteRuleDO matched = routeRuleService.match(req);

        assertNull(matched);
    }

    @Test
    @DisplayName("match 无条件表达式视为恒真命中")
    void matchShouldReturnWhenNoExpression() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("r1");
        rule.setPriority(1);
        rule.setConditionExpr(null);
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        MsgRouteRuleDO matched = routeRuleService.match(req);

        assertNotNull(matched);
    }

    @Test
    @DisplayName("listEnabled 返回启用规则")
    void listEnabledShouldReturnEnabledRules() {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setStatus("ENABLED");
        when(msgRouteRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        assertEquals(1, routeRuleService.listEnabled().size());
    }
}
