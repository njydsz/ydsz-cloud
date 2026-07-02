package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务错误码单元测试
 *
 * <p>验证错误码段位规划、唯一性与提示信息完整性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("BizErrorCode 错误码测试")
class BizErrorCodeTest {

    @Test
    @DisplayName("OK 码应为 0")
    void okCode() {
        assertThat(BizErrorCode.OK.getCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("1xxxx 段为通用错误")
    void genericErrors() {
        assertThat(BizErrorCode.BAD_REQUEST.getCode()).isBetween(10000, 19999);
        assertThat(BizErrorCode.RATE_LIMIT.getCode()).isBetween(10000, 19999);
    }

    @Test
    @DisplayName("2xxxx 段为认证授权错误")
    void authErrors() {
        assertThat(BizErrorCode.UNAUTHORIZED.getCode()).isBetween(20000, 29999);
        assertThat(BizErrorCode.FORBIDDEN.getCode()).isBetween(20000, 29999);
    }

    @Test
    @DisplayName("3xxxx 段为用户/组织/人员错误")
    void userErrors() {
        assertThat(BizErrorCode.USER_NOT_FOUND.getCode()).isBetween(30000, 39999);
        assertThat(BizErrorCode.DEPARTMENT_NOT_FOUND.getCode()).isBetween(30000, 39999);
    }

    @Test
    @DisplayName("4xxxx 段为项目/合同/商机错误")
    void projectErrors() {
        assertThat(BizErrorCode.PROJECT_NOT_FOUND.getCode()).isBetween(40000, 49999);
        assertThat(BizErrorCode.OPPORTUNITY_NOT_FOUND.getCode()).isBetween(40000, 49999);
    }

    @Test
    @DisplayName("5xxxx 段为财务相关错误")
    void financeErrors() {
        assertThat(BizErrorCode.COST_OVERFLOW.getCode()).isBetween(50000, 59999);
        assertThat(BizErrorCode.INVOICE_EXCEED.getCode()).isBetween(50000, 59999);
    }

    @Test
    @DisplayName("6xxxx 段为资源/工时错误")
    void resourceErrors() {
        assertThat(BizErrorCode.RESOURCE_CONFLICT.getCode()).isBetween(60000, 69999);
        assertThat(BizErrorCode.TIMESHEET_DUPLICATE.getCode()).isBetween(60000, 69999);
    }

    @Test
    @DisplayName("7xxxx 段为工作流错误")
    void workflowErrors() {
        assertThat(BizErrorCode.WORKFLOW_NOT_FOUND.getCode()).isBetween(70000, 79999);
    }

    @Test
    @DisplayName("所有错误码 code 不重复")
    void codeUnique() {
        BizErrorCode[] codes = BizErrorCode.values();
        long distinct = java.util.Arrays.stream(codes).mapToInt(BizErrorCode::getCode).distinct().count();
        assertThat(distinct).isEqualTo(codes.length);
    }

    @Test
    @DisplayName("所有错误码 message 不为空")
    void messageNotEmpty() {
        for (BizErrorCode code : BizErrorCode.values()) {
            assertThat(code.getMessage()).isNotBlank();
        }
    }
}
