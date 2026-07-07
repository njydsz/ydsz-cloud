/**
 * 工作流三方对接层（企业微信 / 钉钉 / 飞书）。
 *
 * <p>负责审批中心与三方办公平台的桥接，包括回调签名验证、消息加解密、审批动作适配等。
 * 本包仅承载"通信与协议层"职责，<strong>不直接写流程表</strong>，所有流程操作通过
 * {@code com.njydsz.pmis.workflow.facade} / {@code com.njydsz.pmis.workflow.service} 完成。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.thirdparty.WeComSignatureUtil} - 企业微信回调签名验证
 *   （SHA1 摘要 + 字典序排序）</li>
 *   <li>{@link com.njydsz.pmis.workflow.thirdparty.DingTalkSignatureUtil} - 钉钉回调签名验证工具</li>
 *   <li>{@link com.njydsz.pmis.workflow.thirdparty.FeishuSignatureUtil} - 飞书回调签名验证工具</li>
 *   <li>{@link com.njydsz.pmis.workflow.thirdparty.ThirdPartyApprovalActionResolver} - 三方审批动作适配器，
 *   将"通过 / 驳回 / 重新发起"等三方约定动作映射为 PMIS 内部 {@code FlowTaskOperateDTO}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>工具类（Util）以 {@code final} + 私有构造方法禁止实例化，方法均为 {@code public static}。</li>
 *   <li>签名验证失败一律拒绝（<strong>fail-closed</strong>），避免回调重放 / 伪造。</li>
 *   <li>不同平台配置（{@code CorpID} / {@code AgentID} / {@code SuiteKey}）统一在
 *       {@code pmis_flow_third_party_account} 表中维护，<strong>禁止硬编码</strong>。</li>
 *   <li>本包<strong>不包含电子签章</strong>，合同签署走独立电子签章服务。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.thirdparty;
