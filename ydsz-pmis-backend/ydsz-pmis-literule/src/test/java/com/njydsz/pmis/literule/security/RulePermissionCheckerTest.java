package com.njydsz.pmis.literule.security;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RulePermissionChecker 单元测试（P2-4 按目录授权）
 *
 * <p>覆盖权限校验器的核心能力：
 * <ul>
 *   <li>全目录权限向后兼容（无 categoryPath 段时匹配任意路径）</li>
 *   <li>精确路径匹配（如 "finance" 匹配 "finance"）</li>
 *   <li>子目录匹配（如 "finance" 匹配 "finance/credit"）</li>
 *   <li>{@code *} 单级通配符匹配（匹配单层目录）</li>
 *   <li>{@code **} 多级通配符匹配（跨多层目录）</li>
 *   <li>空 path 不匹配类别特定 pattern（安全修复验证）</li>
 *   <li>{@link #hasPermissionForRule} 通过 configProvider 查询 categoryPath</li>
 *   <li>{@link #filterUnauthorized} 批量校验</li>
 *   <li>{@link #collectMatchingPatterns} 收集匹配模式</li>
 * </ul>
 *
 * <p>测试风格参考 {@code DefaultRuleEngineTest}：JUnit 5 + AssertJ + Mockito.mock 手动创建。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@DisplayName("RulePermissionChecker 按目录授权（P2-4）")
class RulePermissionCheckerTest {

    /** 测试操作人 */
    private static final String OPERATOR = "zhangsan";

    private RuleConfigProvider configProvider;
    private RulePermissionChecker checker;

    @BeforeEach
    void setUp() {
        // 手动 mock RuleConfigProvider，避免引入 MockitoExtension
        configProvider = Mockito.mock(RuleConfigProvider.class);
        checker = new RulePermissionChecker(configProvider);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造带 categoryPath 的 RuleDefinition 测试桩
     *
     * @param ruleCode      规则编码
     * @param categoryPath  分类路径（可为 null）
     * @param category      一级分类（categoryPath 为空时回退使用）
     */
    private RuleDefinition mockDefinition(String ruleCode, String categoryPath, String category) {
        return RuleDefinition.builder()
                .code(ruleCode)
                .name("规则-" + ruleCode)
                .categoryPath(categoryPath)
                .category(category)
                .build();
    }

    // ==================== 全目录权限（向后兼容） ====================

    @Nested
    @DisplayName("全目录权限（向后兼容）")
    class GlobalPermissionTest {

        @Test
        @DisplayName("无 categoryPath 段 - 匹配任意路径")
        void shouldMatchAnyPathWhenNoCategorySegment() {
            assertThat(checker.hasPermission("execution:rule:save", "finance/credit/loan", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermission("execution:rule:save", "finance", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermission("execution:rule:save", "", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermission("execution:rule:save", null, OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("两段式权限编码 - 视为全目录权限")
        void shouldTreatTwoSegmentsAsGlobalPermission() {
            // namespace:action 格式（仅两段），segments.length=2 <= 3，视为全目录权限
            assertThat(checker.hasPermission("execution:save", "finance/credit", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("单段权限编码 - 视为全目录权限")
        void shouldTreatSingleSegmentAsGlobalPermission() {
            // 极端情况：单段权限编码也视为全目录权限（向后兼容）
            assertThat(checker.hasPermission("execution", "finance/credit", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("权限编码为空 - 返回 false")
        void shouldReturnFalseWhenPermissionBlank() {
            assertThat(checker.hasPermission(null, "finance", OPERATOR)).isFalse();
            assertThat(checker.hasPermission("", "finance", OPERATOR)).isFalse();
            assertThat(checker.hasPermission("   ", "finance", OPERATOR)).isFalse();
        }

        @Test
        @DisplayName("categoryPath 段为空 - 视为全目录权限")
        void shouldTreatEmptyCategorySegmentAsGlobalPermission() {
            // execution:rule:save:（第 4 段为空）视为全目录权限
            assertThat(checker.hasPermission("execution:rule:save:", "finance/credit", OPERATOR))
                    .isTrue();
        }
    }

    // ==================== 精确路径与前缀匹配 ====================

    @Nested
    @DisplayName("精确路径与前缀匹配")
    class PrefixMatchTest {

        @Test
        @DisplayName("精确匹配 - pattern 等于 path")
        void shouldMatchExactPath() {
            assertThat(checker.hasPermission("execution:rule:save:finance", "finance", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("子目录匹配 - pattern 是 path 的父目录")
        void shouldMatchSubDirectory() {
            assertThat(checker.hasPermission("execution:rule:save:finance", "finance/credit", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermission("execution:rule:save:finance", "finance/credit/loan", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("多级路径精确匹配")
        void shouldMatchMultiLevelExactPath() {
            assertThat(checker.hasPermission("execution:rule:save:finance/credit", "finance/credit", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("多级路径子目录匹配")
        void shouldMatchMultiLevelSubDirectory() {
            assertThat(checker.hasPermission("execution:rule:save:finance/credit", "finance/credit/loan", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("pattern 是 path 的子目录 - 不匹配（无权限）")
        void shouldNotMatchWhenPatternIsSubDirectoryOfPath() {
            // pattern="finance/credit", path="finance"：path 是 pattern 的父目录，无权限
            assertThat(checker.hasPermission("execution:rule:save:finance/credit", "finance", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("不同根目录 - 不匹配")
        void shouldNotMatchDifferentRoot() {
            assertThat(checker.hasPermission("execution:rule:save:finance", "risk/credit", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("空 path - 类别特定权限不匹配（安全修复）")
        void shouldNotMatchEmptyPathForCategorySpecificPermission() {
            // 安全修复：原实现返回 true（漏洞），现改为返回 false
            assertThat(checker.hasPermission("execution:rule:save:finance", "", OPERATOR))
                    .isFalse();
            assertThat(checker.hasPermission("execution:rule:save:finance", null, OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("路径前后空白 - 自动 trim 后匹配")
        void shouldTrimPathBeforeMatch() {
            assertThat(checker.hasPermission("execution:rule:save:finance", "  finance  ", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermission("execution:rule:save:  finance  ", "finance", OPERATOR))
                    .isTrue();
        }
    }

    // ==================== 单级通配符 * ====================

    @Nested
    @DisplayName("单级通配符 *")
    class SingleWildcardTest {

        @Test
        @DisplayName("* 匹配单级目录")
        void shouldMatchSingleLevelDirectory() {
            // finance/* 匹配 finance/credit
            assertThat(checker.hasPermission("execution:rule:save:finance/*", "finance/credit", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("* 不匹配多级目录")
        void shouldNotMatchMultiLevelDirectory() {
            // finance/* 不匹配 finance/credit/loan（* 仅匹配单级）
            assertThat(checker.hasPermission("execution:rule:save:finance/*", "finance/credit/loan", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("* 不匹配 finance 本身")
        void shouldNotMatchParentOfWildcard() {
            // finance/* 不匹配 finance（* 需要至少一个段）
            assertThat(checker.hasPermission("execution:rule:save:finance/*", "finance", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("* 不匹配空 path")
        void shouldNotMatchEmptyPath() {
            assertThat(checker.hasPermission("execution:rule:save:finance/*", "", OPERATOR))
                    .isFalse();
            assertThat(checker.hasPermission("execution:rule:save:finance/*", null, OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("顶级 * 匹配任意单级目录")
        void shouldMatchAnySingleLevelDirectory() {
            // * 匹配 finance / risk / cost 等任意单级
            assertThat(checker.hasPermission("execution:rule:save:*", "finance", OPERATOR)).isTrue();
            assertThat(checker.hasPermission("execution:rule:save:*", "risk", OPERATOR)).isTrue();
            assertThat(checker.hasPermission("execution:rule:save:*", "cost", OPERATOR)).isTrue();
        }

        @Test
        @DisplayName("顶级 * 不匹配多级目录")
        void shouldNotMatchMultiLevelForTopLevelWildcard() {
            assertThat(checker.hasPermission("execution:rule:save:*", "finance/credit", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("顶级 * 不匹配空 path")
        void shouldNotMatchEmptyPathForTopLevelWildcard() {
            assertThat(checker.hasPermission("execution:rule:save:*", "", OPERATOR)).isFalse();
            assertThat(checker.hasPermission("execution:rule:save:*", null, OPERATOR)).isFalse();
        }
    }

    // ==================== 多级通配符 ** ====================

    @Nested
    @DisplayName("多级通配符 **")
    class DoubleWildcardTest {

        @Test
        @DisplayName("** 匹配多级目录")
        void shouldMatchMultiLevelDirectory() {
            // finance/** 匹配 finance/credit/loan
            assertThat(checker.hasPermission("execution:rule:save:finance/**", "finance/credit/loan", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("** 匹配单级子目录")
        void shouldMatchSingleLevelSubDirectory() {
            // finance/** 匹配 finance/credit
            assertThat(checker.hasPermission("execution:rule:save:finance/**", "finance/credit", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("** 匹配 0 级子目录（path 等于 pattern 前缀部分）")
        void shouldMatchZeroLevelSubDirectory() {
            // finance/** 匹配 finance（** 可匹配 0 个段）
            assertThat(checker.hasPermission("execution:rule:save:finance/**", "finance", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("顶级 ** 匹配任意路径")
        void shouldMatchAnyPathForTopLevelDoubleWildcard() {
            // ** 匹配任意路径（含多级）
            assertThat(checker.hasPermission("execution:rule:save:**", "finance", OPERATOR)).isTrue();
            assertThat(checker.hasPermission("execution:rule:save:**", "finance/credit/loan", OPERATOR)).isTrue();
        }

        @Test
        @DisplayName("顶级 ** 匹配空 path（** 可匹配 0 个段）")
        void shouldMatchEmptyPathForTopLevelDoubleWildcard() {
            // ** 拆段后为 ["**"]，path="" 拆段为 []，pattern 以 ** 结尾返回 true
            // ** 段匹配 0 个或多个段，path 为空时仍匹配
            assertThat(checker.hasPermission("execution:rule:save:**", "", OPERATOR)).isTrue();
            assertThat(checker.hasPermission("execution:rule:save:**", null, OPERATOR)).isTrue();
        }

        @Test
        @DisplayName("** 不匹配其他根目录")
        void shouldNotMatchDifferentRootForDoubleWildcard() {
            // finance/** 不匹配 risk/credit
            assertThat(checker.hasPermission("execution:rule:save:finance/**", "risk/credit", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("中间 ** 段匹配多级")
        void shouldMatchMiddleDoubleWildcard() {
            // finance/**/loan 匹配 finance/credit/loan / finance/credit/sub/loan
            assertThat(checker.hasPermission("execution:rule:save:finance/**/loan", "finance/credit/loan", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermission("execution:rule:save:finance/**/loan", "finance/credit/sub/loan", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("中间 ** 段匹配 0 级")
        void shouldMatchMiddleDoubleWildcardZeroLevel() {
            // finance/**/loan 匹配 finance/loan（** 匹配 0 级）
            assertThat(checker.hasPermission("execution:rule:save:finance/**/loan", "finance/loan", OPERATOR))
                    .isTrue();
        }
    }

    // ==================== hasPermissionForRule ====================

    @Nested
    @DisplayName("hasPermissionForRule")
    class HasPermissionForRuleTest {

        @Test
        @DisplayName("规则存在且有 categoryPath - 按 categoryPath 校验")
        void shouldCheckByCategoryPathWhenPresent() {
            RuleDefinition def = mockDefinition("RISK_001", "finance/credit/loan", "finance");
            when(configProvider.findByCode("RISK_001")).thenReturn(def);

            // 全目录权限 - 通过
            assertThat(checker.hasPermissionForRule("execution:rule:save", "RISK_001", OPERATOR))
                    .isTrue();
            // finance 子目录权限 - 通过
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance", "RISK_001", OPERATOR))
                    .isTrue();
            // finance/** 多级通配 - 通过
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance/**", "RISK_001", OPERATOR))
                    .isTrue();
            // risk 目录权限 - 拒绝
            assertThat(checker.hasPermissionForRule("execution:rule:save:risk", "RISK_001", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("规则存在但 categoryPath 为空 - 回退到 category 字段")
        void shouldFallbackToCategoryWhenCategoryPathBlank() {
            RuleDefinition def = mockDefinition("RISK_002", null, "finance");
            when(configProvider.findByCode("RISK_002")).thenReturn(def);

            // categoryPath 为空，回退到 category="finance"
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance", "RISK_002", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermissionForRule("execution:rule:save:risk", "RISK_002", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("规则存在但 categoryPath 和 category 均为空 - 类别特定权限拒绝")
        void shouldRejectWhenBothCategoryPathAndCategoryBlank() {
            RuleDefinition def = mockDefinition("RISK_003", null, null);
            when(configProvider.findByCode("RISK_003")).thenReturn(def);

            // 类别特定权限拒绝（空 path 不匹配类别特定 pattern）
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance", "RISK_003", OPERATOR))
                    .isFalse();
            // 全目录权限仍通过
            assertThat(checker.hasPermissionForRule("execution:rule:save", "RISK_003", OPERATOR))
                    .isTrue();
        }

        @Test
        @DisplayName("规则不存在 - 按全目录权限校验（新建规则场景）")
        void shouldFallbackToGlobalWhenRuleNotFound() {
            when(configProvider.findByCode("NON_EXISTENT")).thenReturn(null);

            // 新建规则场景：全目录权限通过
            assertThat(checker.hasPermissionForRule("execution:rule:save", "NON_EXISTENT", OPERATOR))
                    .isTrue();
            // 类别特定权限拒绝（path=null 不匹配类别特定 pattern）
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance", "NON_EXISTENT", OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("ruleCode 为空 - 降级为全目录权限校验")
        void shouldFallbackToGlobalWhenRuleCodeBlank() {
            assertThat(checker.hasPermissionForRule("execution:rule:save", "", OPERATOR))
                    .isTrue();
            assertThat(checker.hasPermissionForRule("execution:rule:save", null, OPERATOR))
                    .isTrue();
            // 类别特定权限拒绝
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance", "", OPERATOR))
                    .isFalse();
            assertThat(checker.hasPermissionForRule("execution:rule:save:finance", null, OPERATOR))
                    .isFalse();
        }

        @Test
        @DisplayName("configProvider 为 null - 降级为全目录权限校验")
        void shouldFallbackToGlobalWhenConfigProviderNull() {
            RulePermissionChecker noProviderChecker = new RulePermissionChecker(null);

            // configProvider=null，hasPermissionForRule 调用 hasPermission(permission, null, operator)
            assertThat(noProviderChecker.hasPermissionForRule("execution:rule:save", "RISK_001", OPERATOR))
                    .isTrue();
            // 类别特定权限拒绝（path=null 不匹配）
            assertThat(noProviderChecker.hasPermissionForRule("execution:rule:save:finance", "RISK_001", OPERATOR))
                    .isFalse();
        }
    }

    // ==================== filterUnauthorized 批量校验 ====================

    @Nested
    @DisplayName("filterUnauthorized 批量校验")
    class FilterUnauthorizedTest {

        @Test
        @DisplayName("全部有权限 - 返回空列表")
        void shouldReturnEmptyWhenAllAuthorized() {
            when(configProvider.findByCode("R1")).thenReturn(mockDefinition("R1", "finance/credit", "finance"));
            when(configProvider.findByCode("R2")).thenReturn(mockDefinition("R2", "finance/loan", "finance"));

            List<String> ruleCodes = Arrays.asList("R1", "R2");
            List<String> unauthorized = checker.filterUnauthorized(
                    "execution:rule:save:finance", ruleCodes, OPERATOR);

            assertThat(unauthorized).isEmpty();
        }

        @Test
        @DisplayName("部分无权限 - 返回无权限列表")
        void shouldReturnUnauthorizedList() {
            when(configProvider.findByCode("R1")).thenReturn(mockDefinition("R1", "finance/credit", "finance"));
            when(configProvider.findByCode("R2")).thenReturn(mockDefinition("R2", "risk/credit", "risk"));
            when(configProvider.findByCode("R3")).thenReturn(mockDefinition("R3", "finance/loan", "finance"));

            List<String> ruleCodes = Arrays.asList("R1", "R2", "R3");
            List<String> unauthorized = checker.filterUnauthorized(
                    "execution:rule:save:finance", ruleCodes, OPERATOR);

            // 仅 R2（risk 目录）无权限
            assertThat(unauthorized).containsExactly("R2");
        }

        @Test
        @DisplayName("全部无权限 - 返回全部规则编码")
        void shouldReturnAllWhenNoneAuthorized() {
            when(configProvider.findByCode("R1")).thenReturn(mockDefinition("R1", "risk/credit", "risk"));
            when(configProvider.findByCode("R2")).thenReturn(mockDefinition("R2", "cost/budget", "cost"));

            List<String> ruleCodes = Arrays.asList("R1", "R2");
            List<String> unauthorized = checker.filterUnauthorized(
                    "execution:rule:save:finance", ruleCodes, OPERATOR);

            assertThat(unauthorized).containsExactlyInAnyOrder("R1", "R2");
        }

        @Test
        @DisplayName("全目录权限 - 全部有权限")
        void shouldReturnEmptyForGlobalPermission() {
            when(configProvider.findByCode("R1")).thenReturn(mockDefinition("R1", "finance/credit", "finance"));
            when(configProvider.findByCode("R2")).thenReturn(mockDefinition("R2", "risk/credit", "risk"));

            List<String> ruleCodes = Arrays.asList("R1", "R2");
            List<String> unauthorized = checker.filterUnauthorized(
                    "execution:rule:save", ruleCodes, OPERATOR);

            assertThat(unauthorized).isEmpty();
        }

        @Test
        @DisplayName("空 ruleCodes - 返回空列表")
        void shouldReturnEmptyForEmptyRuleCodes() {
            List<String> unauthorized = checker.filterUnauthorized(
                    "execution:rule:save:finance", java.util.Collections.emptyList(), OPERATOR);

            assertThat(unauthorized).isEmpty();
        }

        @Test
        @DisplayName("null ruleCodes - 返回空列表")
        void shouldReturnEmptyForNullRuleCodes() {
            List<String> unauthorized = checker.filterUnauthorized(
                    "execution:rule:save:finance", null, OPERATOR);

            assertThat(unauthorized).isEmpty();
        }
    }

    // ==================== collectMatchingPatterns ====================

    @Nested
    @DisplayName("collectMatchingPatterns 收集模式")
    class CollectMatchingPatternsTest {

        @Test
        @DisplayName("收集匹配 namespace:action 的全部模式")
        void shouldCollectMatchingPatterns() {
            List<String> permissions = Arrays.asList(
                    "execution:rule:save:finance",
                    "execution:rule:save:risk/*",
                    "execution:rule:save:cost/**",
                    "execution:rule:save",            // 全目录权限（无 categoryPath 段）
                    "execution:rule:delete:finance",  // 不匹配的 action
                    "other:rule:save:finance"         // 不匹配的 namespace
            );

            Set<String> patterns = checker.collectMatchingPatterns(permissions, "execution", "rule:save");

            // 应包含 4 个匹配模式：finance / risk/* / cost/** / ""（全目录权限）
            assertThat(patterns).containsExactlyInAnyOrder("finance", "risk/*", "cost/**", "");
        }

        @Test
        @DisplayName("全目录权限 - patterns 包含空字符串")
        void shouldIncludeEmptyStringForGlobalPermission() {
            List<String> permissions = Arrays.asList(
                    "execution:rule:save",
                    "execution:rule:save:finance"
            );

            Set<String> patterns = checker.collectMatchingPatterns(permissions, "execution", "rule:save");

            assertThat(patterns).contains("", "finance");
        }

        @Test
        @DisplayName("无匹配权限 - 返回空集合")
        void shouldReturnEmptyWhenNoMatch() {
            List<String> permissions = Arrays.asList(
                    "execution:rule:delete:finance",
                    "other:rule:save:finance"
            );

            Set<String> patterns = checker.collectMatchingPatterns(permissions, "execution", "rule:save");

            assertThat(patterns).isEmpty();
        }

        @Test
        @DisplayName("空权限列表 - 返回空集合")
        void shouldReturnEmptyForEmptyPermissions() {
            Set<String> patterns = checker.collectMatchingPatterns(
                    java.util.Collections.emptyList(), "execution", "rule:save");

            assertThat(patterns).isEmpty();
        }

        @Test
        @DisplayName("null 权限列表 - 返回空集合")
        void shouldReturnEmptyForNullPermissions() {
            Set<String> patterns = checker.collectMatchingPatterns(null, "execution", "rule:save");

            assertThat(patterns).isEmpty();
        }

        @Test
        @DisplayName("权限列表含 null/空白元素 - 跳过")
        void shouldSkipNullAndBlankPermissions() {
            List<String> permissions = Arrays.asList(
                    null,
                    "",
                    "   ",
                    "execution:rule:save:finance"
            );

            Set<String> patterns = checker.collectMatchingPatterns(permissions, "execution", "rule:save");

            assertThat(patterns).containsExactly("finance");
        }

        @Test
        @DisplayName("保持插入顺序（LinkedHashSet）")
        void shouldPreserveInsertionOrder() {
            List<String> permissions = Arrays.asList(
                    "execution:rule:save:cost/**",
                    "execution:rule:save:finance",
                    "execution:rule:save:risk/*"
            );

            Set<String> patterns = checker.collectMatchingPatterns(permissions, "execution", "rule:save");

            // LinkedHashSet 保持插入顺序
            assertThat(patterns).containsExactly("cost/**", "finance", "risk/*");
        }
    }

    // ==================== matchesPath 包级方法直接验证 ====================

    @Nested
    @DisplayName("matchesPath 路径匹配核心逻辑")
    class MatchesPathTest {

        @Test
        @DisplayName("pattern 为空 - 匹配任意 path")
        void shouldMatchAnyPathWhenPatternBlank() {
            assertThat(checker.matchesPath("", "finance/credit")).isTrue();
            assertThat(checker.matchesPath(null, "finance/credit")).isTrue();
            assertThat(checker.matchesPath("  ", "finance/credit")).isTrue();
        }

        @Test
        @DisplayName("复杂多级 ** 匹配")
        void shouldMatchComplexMultiLevelDoubleWildcard() {
            // a/**/b/**/c 匹配 a/x/b/y/z/c
            assertThat(checker.matchesPath("a/**/b/**/c", "a/x/b/y/z/c")).isTrue();
            // a/**/b/**/c 匹配 a/b/c（两个 ** 都匹配 0 级）
            assertThat(checker.matchesPath("a/**/b/**/c", "a/b/c")).isTrue();
        }

        @Test
        @DisplayName("连续 ** 段等价于单个 **")
        void shouldTreatConsecutiveDoubleWildcardsAsSingle() {
            // finance/**/**/loan 等价于 finance/**/loan
            assertThat(checker.matchesPath("finance/**/**/loan", "finance/credit/loan")).isTrue();
            assertThat(checker.matchesPath("finance/**/**/loan", "finance/loan")).isTrue();
            assertThat(checker.matchesPath("finance/**/**/loan", "finance/a/b/c/loan")).isTrue();
        }
    }
}
