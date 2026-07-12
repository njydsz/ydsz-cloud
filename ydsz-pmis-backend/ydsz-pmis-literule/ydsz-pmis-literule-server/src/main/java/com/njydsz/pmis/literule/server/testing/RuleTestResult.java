package com.njydsz.pmis.literule.server.testing;

import com.njydsz.pmis.literule.api.RuleResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 规则测试运行结果
 *
 * <p>单条测试用例执行后的完整结果，包含实际触发的规则、预期触发的规则、
 * 通过/失败判定、详细差异信息等。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 测试用例 ID */
    private String testCaseId;

    /** 测试用例名称 */
    private String testCaseName;

    /** 关联规则编码（可选） */
    private String ruleCode;

    /** 是否通过 */
    private boolean passed;

    /** 实际触发的规则编码集合 */
    private Set<String> actualTriggered;

    /** 预期触发的规则编码集合 */
    private Set<String> expectedTriggered;

    /** 误触发的规则（实际触发但不在预期中） */
    private Set<String> falsePositives;

    /** 漏触发的规则（预期触发但实际未触发） */
    private Set<String> falseNegatives;

    /** 完整评估结果列表 */
    private List<RuleResult> ruleResults;

    /** 评估耗时（毫秒） */
    private long elapsedMs;

    /** 失败原因（passed=false 时填充） */
    private String failureReason;

    /**
     * 快速构建失败结果
     */
    public static RuleTestResult failed(String testCaseId, String testCaseName, String reason, long elapsedMs) {
        return RuleTestResult.builder()
                .testCaseId(testCaseId)
                .testCaseName(testCaseName)
                .passed(false)
                .failureReason(reason)
                .elapsedMs(elapsedMs)
                .build();
    }

    /**
     * 快速构建通过结果
     */
    public static RuleTestResult passed(String testCaseId, String testCaseName, Set<String> triggered, long elapsedMs) {
        return RuleTestResult.builder()
                .testCaseId(testCaseId)
                .testCaseName(testCaseName)
                .passed(true)
                .actualTriggered(triggered)
                .expectedTriggered(triggered)
                .elapsedMs(elapsedMs)
                .build();
    }
}
