package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleAdminService 状态机强制校验测试
 *
 * <p>验证 save 方法在新建/更新场景下对 RuleStatus.canTransitionTo 的强制调用。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class RuleAdminServiceStatusTest {

    private FakeRuleConfigProvider configProvider;
    private RuleAdminService service;

    @BeforeEach
    void setUp() {
        RuleEngine engine = new DefaultRuleEngine();
        AviatorExpressionEvaluator evaluator = new AviatorExpressionEvaluator(false);
        configProvider = new FakeRuleConfigProvider();
        ApplicationEventPublisher publisher = event -> { /* no-op */ };
        service = new RuleAdminService(engine, evaluator, configProvider, null, publisher);
    }

    @Test
    void newRuleWithDraftStatusShouldSucceed() {
        RuleDefinition def = buildDefinition("R_NEW_DRAFT", "DRAFT");
        assertDoesNotThrow(() -> service.save(def, "tester", "新建草稿"));
    }

    @Test
    void newRuleWithPublishedStatusShouldSucceed() {
        RuleDefinition def = buildDefinition("R_NEW_PUB", "PUBLISHED");
        assertDoesNotThrow(() -> service.save(def, "tester", "新建直接发布"));
    }

    @Test
    void newRuleWithReviewStatusShouldBeRejected() {
        RuleDefinition def = buildDefinition("R_NEW_REVIEW", "REVIEW");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.save(def, "tester", "非法初始状态"));
        assertTrue(ex.getMessage().contains("初始状态只能为 DRAFT 或 PUBLISHED"));
    }

    @Test
    void newRuleWithArchivedStatusShouldBeRejected() {
        RuleDefinition def = buildDefinition("R_NEW_ARCH", "ARCHIVED");
        assertThrows(IllegalStateException.class,
                () -> service.save(def, "tester", "非法初始状态"));
    }

    @Test
    void newRuleWithDisabledStatusShouldBeRejected() {
        RuleDefinition def = buildDefinition("R_NEW_DIS", "DISABLED");
        assertThrows(IllegalStateException.class,
                () -> service.save(def, "tester", "非法初始状态"));
    }

    @Test
    void updatePublishedToDisabledShouldSucceed() {
        seedExisting("R_P2D", "PUBLISHED");
        RuleDefinition def = buildDefinition("R_P2D", "DISABLED");
        assertDoesNotThrow(() -> service.save(def, "tester", "停用已发布规则"));
    }

    @Test
    void updatePublishedToArchivedShouldSucceed() {
        seedExisting("R_P2A", "PUBLISHED");
        RuleDefinition def = buildDefinition("R_P2A", "ARCHIVED");
        assertDoesNotThrow(() -> service.save(def, "tester", "归档已发布规则"));
    }

    @Test
    void updatePublishedToDraftShouldBeRejected() {
        seedExisting("R_P2DRAFT", "PUBLISHED");
        RuleDefinition def = buildDefinition("R_P2DRAFT", "DRAFT");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.save(def, "tester", "非法回退"));
        assertTrue(ex.getMessage().contains("不允许的状态转换"));
        assertTrue(ex.getMessage().contains("已发布"));
        assertTrue(ex.getMessage().contains("草稿"));
    }

    @Test
    void updatePublishedToPublishedShouldSucceedWhenStatusUnchanged() {
        // 状态未变化时直接放行（仅修改表达式等字段）
        seedExisting("R_SAME", "PUBLISHED");
        RuleDefinition def = buildDefinition("R_SAME", "PUBLISHED");
        def.setConditionExpression("2 > 1");
        assertDoesNotThrow(() -> service.save(def, "tester", "修改表达式保持状态"));
    }

    @Test
    void updateArchivedToAnyStatusShouldBeRejected() {
        // ARCHIVED 是终态
        seedExisting("R_ARCH", "ARCHIVED");
        RuleDefinition def = buildDefinition("R_ARCH", "DRAFT");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.save(def, "tester", "归档态复活"));
        assertTrue(ex.getMessage().contains("不允许的状态转换"));
    }

    @Test
    void updateDisabledToPublishedShouldSucceed() {
        seedExisting("R_D2P", "DISABLED");
        RuleDefinition def = buildDefinition("R_D2P", "PUBLISHED");
        assertDoesNotThrow(() -> service.save(def, "tester", "重新启用"));
    }

    @Test
    void updateReviewToPublishedShouldSucceed() {
        seedExisting("R_RV2P", "REVIEW");
        RuleDefinition def = buildDefinition("R_RV2P", "PUBLISHED");
        assertDoesNotThrow(() -> service.save(def, "tester", "审核通过"));
    }

    @Test
    void nullStatusShouldSkipTransitionCheck() {
        // status 为空时跳过校验（向后兼容，由数据库默认值生效）
        RuleDefinition def = RuleDefinition.builder()
                .code("R_NULL_STATUS")
                .name("空状态规则")
                .conditionExpression("1 > 0")
                .severityExpression(null)
                .status(null)
                .build();
        assertDoesNotThrow(() -> service.save(def, "tester", "空状态跳过校验"));
    }

    @Test
    void illegalStatusValueShouldBeRejected() {
        RuleDefinition def = buildDefinition("R_BAD", "PENDING");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(def, "tester", "非法状态值"));
        assertTrue(ex.getMessage().contains("非法的规则状态"));
        assertTrue(ex.getMessage().contains("PENDING"));
    }

    // ---------- 辅助方法 ----------

    private RuleDefinition buildDefinition(String code, String status) {
        return RuleDefinition.builder()
                .code(code)
                .name(code)
                .conditionExpression("1 > 0")
                .severityExpression(null)
                .status(status)
                .build();
    }

    private void seedExisting(String code, String status) {
        RuleDefinition existing = RuleDefinition.builder()
                .code(code)
                .name(code)
                .conditionExpression("1 > 0")
                .status(status)
                .version(1)
                .build();
        configProvider.store.put(code, existing);
    }

    /**
     * 内存版 RuleConfigProvider，用于测试状态转换校验
     */
    static class FakeRuleConfigProvider implements RuleConfigProvider {
        final Map<String, RuleDefinition> store = new ConcurrentHashMap<>();

        @Override
        public List<RuleDefinition> loadEnabledRules() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<RuleDefinition> loadAllRules() {
            return new ArrayList<>(store.values());
        }

        @Override
        public RuleDefinition save(RuleDefinition definition, String operator) {
            RuleDefinition saved = RuleDefinition.builder()
                    .code(definition.getCode())
                    .name(definition.getName())
                    .conditionExpression(definition.getConditionExpression())
                    .severityExpression(definition.getSeverityExpression())
                    .status(definition.getStatus())
                    .version(definition.getVersion() > 0 ? definition.getVersion() + 1 : 1)
                    .build();
            store.put(saved.getCode(), saved);
            return saved;
        }

        @Override
        public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
            // no-op for test
        }

        @Override
        public RuleDefinition findByCode(String ruleCode) {
            return store.get(ruleCode);
        }
    }
}
