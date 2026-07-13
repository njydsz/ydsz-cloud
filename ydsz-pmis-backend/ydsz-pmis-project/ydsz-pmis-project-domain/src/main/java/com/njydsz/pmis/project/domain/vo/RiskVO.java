package com.njydsz.pmis.project.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 项目风险 VO（对外接口返回视图）
 *
 * <p>从 {@link com.njydsz.pmis.project.domain.entity.RiskDO} 转换而来，
 * 剥离了敏感字段：{@code tenantId}、{@code providerTraceId}、{@code deleted}、{@code version}（乐观锁版本号）。
 *
 * <p>设计参考：{@code com.njydsz.pmis.userinfo.domain.vo.UserVO} 的 DO/VO 分离模式。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 风险编号 */
    private String riskCode;
    /** 项目立项ID */
    private String initiationId;
    /** 风险标题 */
    private String riskTitle;
    /** 风险类型：SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER */
    private String riskType;
    /** 风险描述 */
    private String description;
    /** 发生概率：LOW/MEDIUM/HIGH */
    private String probability;
    /** 影响程度：LOW/MEDIUM/HIGH */
    private String impact;
    /** 计算后的风险等级 */
    private String riskLevel;
    /** 应对策略 */
    private String mitigation;
    /** 应急预案 */
    private String contingency;
    /** 责任人ID */
    private String ownerId;
    /** 责任人姓名 */
    private String ownerName;
    /** 状态：RiskStatus.code */
    private String status;
    /** 风险发生时间 */
    private LocalDateTime occurredAt;
    /** 风险关闭时间 */
    private LocalDateTime closedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
