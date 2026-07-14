package com.njydsz.pmis.common.docs.domain;

import java.util.List;

import com.njydsz.pmis.common.docs.enums.SecurityLevel;

import lombok.Builder;
import lombok.Data;

/**
 * 安全扫描结果
 * <p>
 * 文档安全扫描的完整输出，包含安全等级和风险项列表。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Data
@Builder
public class SecurityScanResult {

    /** 安全等级 */
    private SecurityLevel securityLevel;

    /** 风险项列表 */
    private List<SecurityFinding> findings;

    /** 扫描是否成功 */
    private boolean success;

    /** 错误消息 */
    private String errorMessage;

    /**
     * 安全风险项
     */
    @Data
    @Builder
    public static class SecurityFinding {

        /** 风险类型（macro/embedded_object/pdf_js/external_link 等） */
        private String type;

        /** 风险描述 */
        private String description;

        /** 风险位置（如页码、段落索引） */
        private String location;

        /** 风险等级 */
        private SecurityLevel level;
    }
}
