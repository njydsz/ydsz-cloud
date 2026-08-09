package com.njydsz.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程节点实体
 *
 * <p>对应数据库表 {@code ydsz_flow_node}，对标 Warm-Flow {@code flow_node}，
 * 描述流程定义中的每个节点（开始/审批/网关/结束），是流程图渲染与引擎调度的最小单元。
 *
 * <p><b>节点分类：</b>
 * <ul>
 *   <li><b>开始节点（{@code nodeType=0}）</b>：流程入口，每个流程定义唯一</li>
 *   <li><b>审批节点（{@code nodeType=1}）</b>：人工审批环节，需配置 {@code permissionFlag} 办理人规则</li>
 *   <li><b>网关节点（{@code nodeType=2}）</b>：并行/排他/包容网关，控制流转分支</li>
 *   <li><b>结束节点（{@code nodeType=3}）</b>：流程出口，可配置多个</li>
 *   <li><b>子流程节点（{@code nodeType=4}）</b>：嵌套调用其它流程定义</li>
 *   <li><b>抄送节点（{@code nodeType=5}）</b>：仅通知，不阻塞流程</li>
 * </ul>
 *
 * <p><b>办理人解析：</b>{@code permissionFlag} 支持多类前缀：
 * <ul>
 *   <li>{@code role:hr} — 指定角色（如 {@code hr} / {@code pm}）</li>
 *   <li>{@code dept:10} — 指定部门（ID/编码）</li>
 *   <li>{@code user:1001} — 指定用户（ID/账号）</li>
 *   <li>{@code post:dev} — 指定岗位（编码）</li>
 *   <li>{@code initiator} — 发起人本人</li>
 *   <li>{@code initiatorLeader} — 发起人直属上级</li>
 *   <li>{@code ${spel}} — SpEL 表达式动态解析</li>
 * </ul>
 *
 * <p><b>设计器渲染：</b>{@code coordinate} 存储 bpmn-js 设计器画布坐标（{@code {x, y, width, height}}），
 * 流程图回显与节点拖拽定位均依赖该字段。
 *
 * <p><b>扩展配置（{@code ext}）支持的 key：</b>
 * <ul>
 *   <li>{@code priority} — 节点优先级（默认 50，用于多任务排序）</li>
 *   <li>{@code emptyStrategy} — 审批人为空兜底策略（{@code FALLBACK} / {@code AUTO_PASS} / {@code TRANSFER_ADMIN} / {@code ASSIGN_SPECIFIED}）</li>
 *   <li>{@code collection} — 会签人员集合变量名（如 {@code ${approvers}}）</li>
 *   <li>{@code votePassRate} — 票签通过率（{@code 0~1}）</li>
 *   <li>{@code userWeights} — 加权票签权重映射（{@code userId -> weight}）</li>
 *   <li>{@code autoDedup} — 是否启用跨节点办理人去重</li>
 *   <li>GAP-P2-9 {@code freeJump} — 是否允许自由流跳转到该节点（{@code true/false}，默认 {@code false}）</li>
 * </ul>
 *
 * <p><b>表单字段权限（{@code formFieldsConfig}）：</b>按节点控制审批表单字段的可编辑/只读/隐藏，
 * 格式为 {@code {"fieldKey":"EDIT|READONLY|HIDDEN",...}}，由前端渲染层解析应用。
 *
 * <p><b>SLA 超时配置（{@code slaConfig}）：</b>JSON 结构，包含超时时长、触发动作、提醒次数、兜底处理人。
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>唯一索引 {@code uk_definition_node_code}（{@code definition_id}, {@code node_code}）：流程内节点编码唯一</li>
 *   <li>普通索引 {@code idx_flow_code}（{@code flow_code}）：加速按流程编码批量查询</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDefinition 流程定义
 * @see com.njydsz.workflow.domain.enums.FlowNodeType 节点类型枚举
 * @see com.njydsz.workflow.server.engine.AssignmentResolver 办理人解析器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_node")
public class FlowNode extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属流程定义 ID（关联 {@code ydsz_flow_definition.id}） */
    private String definitionId;

    /** 流程编码（冗余字段，避免 JOIN 流程定义） */
    private String flowCode;

    /**
     * 节点类型（{@link com.njydsz.workflow.domain.enums.FlowNodeType}.code）。
     *
     * <p>取值：{@code 0}=开始 / {@code 1}=审批 / {@code 2}=网关 / {@code 3}=结束 / {@code 4}=子流程 / {@code 5}=抄送
     */
    private Integer nodeType;

    /** 节点编码（流程内唯一，用于 SpEL 引用与跳转） */
    private String nodeCode;

    /** 节点名称（设计器展示与审批页标题） */
    private String nodeName;

    /**
     * 办理人权限标识。
     *
     * <p>支持的前缀：{@code role:xxx} / {@code dept:xxx} / {@code user:xxx} / {@code post:xxx} /
     * {@code initiator} / {@code initiatorLeader} / {@code ${spel}}。
     * 多个规则用 {@code ,} 分隔，由 {@code AssignmentResolver} 解析展开。
     */
    private String permissionFlag;

    /**
     * 任意跳转目标节点编码。
     *
     * <p>允许审批人「任意跳转」功能时可用的目标节点集合（{@code ,} 分隔），为空表示不允许任意跳转。
     */
    private String skipAnyNode;

    /**
     * 设计器坐标 JSON。
     *
     * <p>格式：{@code {"x":100,"y":200,"width":120,"height":60}}，由 bpmn-js 设计器生成。
     */
    private String coordinate;

    /**
     * 节点跳转路由集合 JSON。
     *
     * <p>存储节点出度列表，格式：{@code [{"toNodeCode":"node_2","condition":"${amount>1000}","priority":1},...]}。
     * 引擎按优先级匹配首个满足条件的路由。
     */
    private String skipList;

    /**
     * 扩展字段 JSON。
     *
     * <p>支持的配置项：
     * <ul>
     *   <li>{@code priority}：节点优先级（默认 50）</li>
     *   <li>{@code emptyStrategy}：审批人为空兜底策略（FALLBACK/AUTO_PASS/TRANSFER_ADMIN/ASSIGN_SPECIFIED）</li>
     *   <li>{@code collection}：会签人员集合变量名（如 {@code ${approvers}}）</li>
     *   <li>{@code votePassRate}：票签通过率（0~1）</li>
     *   <li>{@code userWeights}：加权票签权重映射（userId -> weight）</li>
     *   <li>{@code autoDedup}：是否启用跨节点办理人去重</li>
     *   <li>GAP-P2-9 {@code freeJump}：是否允许自由流跳转到该节点（true/false，默认 false）</li>
     * </ul>
     */
    private String ext;

    /**
     * 表单字段权限配置 JSON。
     *
     * <p>按节点控制字段可编辑/只读/隐藏，格式：{@code {"fieldKey":"EDIT|READONLY|HIDDEN",...}}。
     * 由前端审批表单渲染时按当前节点配置应用。
     */
    private String formFieldsConfig;

    /**
     * SLA 超时配置 JSON。
     *
     * <p>格式：{@code {"timeoutMinutes":120,"action":"REMIND|ESCALATE|AUTO_PASS|AUTO_REJECT","ydsznderCount":3,"adminUserId":"xxx"}}。
     * 由 {@code SlaMonitorScheduler} 周期性扫描超时任务并触发对应动作。
     */
    private String slaConfig;

    /** 链路追踪 ID（关联 MDC traceId，用于跨服务追踪） */
    private String providerTraceId;
}
