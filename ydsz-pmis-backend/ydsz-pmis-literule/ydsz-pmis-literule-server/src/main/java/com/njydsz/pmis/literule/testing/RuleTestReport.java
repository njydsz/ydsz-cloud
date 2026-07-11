package com.njydsz.pmis.literule.server.testing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 规则测试套件运行报告
 *
 * <p>批量执行测试用例后的聚合报告，包含通过率统计、失败详情、耗时等。
 * 可用于 CI/CD 流水线门禁：当 {@link #passRate} < 100% 时阻断发布。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 测试套件名称 */
    private String suiteName;

    /** 总用例数 */
    private int total;

    /** 通过数 */
    private int passed;

    /** 失败数 */
    private int failed;

    /** 跳过数 */
    private int skipped;

    /** 通过率（百分比） */
    private String passRate;

    /** 总耗时（毫秒） */
    private long totalElapsedMs;

    /** 每条用例的详细结果 */
    private List<RuleTestResult> results;

    /** 失败用例的详细结果（便于快速定位） */
    private List<RuleTestResult> failedResults;

    /**
     * 是否全部通过
     */
    public boolean allPassed() {
        return failed == 0 && skipped == 0;
    }

    /**
     * 计算通过率
     */
    public static String calculatePassRate(int passed, int total) {
        if (total == 0) return "100.0%";
        double rate = (double) passed / total * 100;
        return String.format("%.1f%%", rate);
    }
}
