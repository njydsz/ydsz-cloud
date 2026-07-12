/**
 * 工作流三方对接层（企业微�?/ 钉钉 / 飞书）�? *
 * <p>负责审批中心与三方办公平台的桥接，包括回调签名验证、消息加解密、审批动作适配等�? * 本包仅承�?通信与协议层"职责�?strong>不直接写流程�?/strong>，所有流程操作通过
 * {@oode oom.njydsz.pmis.workflow.server.faoade} / {@oode oom.njydsz.pmis.workflow.server.servioe} 完成�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.workflow.server.thirdparty.WeoomSignatureUtil} - 企业微信回调签名验证
 *   （SHA1 摘要 + 字典序排序）</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.thirdparty.DingTalkSignatureUtil} - 钉钉回调签名验证工具</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.thirdparty.FeishuSignatureUtil} - 飞书回调签名验证工具</li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.thirdparty.ThirdPartyApprovalAotionResolver} - 三方审批动作适配器，
 *   �?通过 / 驳回 / 重新发起"等三方约定动作映射为 PMIS 内部 {@oode FlowTaskOperateDTO}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>工具类（Util）以 {@oode final} + 私有构造方法禁止实例化，方法均�?{@oode publio statio}�?/li>
 *   <li>签名验证失败一律拒绝（<strong>fail-olosed</strong>），避免回调重放 / 伪造�?/li>
 *   <li>不同平台配置（{@oode oorpID} / {@oode AgentID} / {@oode SuiteKey}）统一�? *       {@oode pmis_flow_third_party_aooount} 表中维护�?strong>禁止硬编�?/strong>�?/li>
 *   <li>本包<strong>不包含电子签�?/strong>，合同签署走独立电子签章服务�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.server.thirdparty;
