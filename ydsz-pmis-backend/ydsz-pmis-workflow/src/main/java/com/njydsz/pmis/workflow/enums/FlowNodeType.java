package com.njydsz.pmis.workflow.enums;

/**
 * 流程节点类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FlowNodeType {

    /** 开始节点 */
    START(0, "开始"),
    /** 审批节点（单人审批） */
    APPROVAL(1, "审批"),
    /** 抄送节点 */
    CC(2, "抄送"),
    /** 条件路由节点（互斥网关） */
    CONDITION(3, "条件"),
    /** 并行网关（同时推进多条分支） */
    PARALLEL(4, "并行网关"),
    /** 包容网关（满足条件的分支都推进） */
    INCLUSIVE(5, "包容网关"),
    /** 结束节点 */
    END(6, "结束"),
    /** 子流程节点 */
    SUBPROCESS(7, "子流程"),
    /** P1-4: 服务节点 — 自动执行（HTTP/SCRIPT/AUTO_PASS），不创建人工任务。
     *  <p>ext JSON 配置：
     *  <ul>
     *    <li>serviceType: HTTP / SCRIPT / AUTO_PASS（默认 AUTO_PASS）</li>
     *    <li>url: HTTP 调用地址（serviceType=HTTP 时必填）</li>
     *    <li>method: HTTP 方法 GET/POST/PUT/DELETE（默认 GET）</li>
     *    <li>script: 脚本内容（serviceType=SCRIPT 时使用，Aviator 语法，由 FlowServiceNodeExecutor 沙箱执行）</li>
     *  </ul>
     *  <p>P1-5: ext JSON 还支持 autoDedup: true 配置，表示该节点启用跨节点办理人去重
     *  （同实例下已审批过的办理人将被排除，候选为空时自动跳过）。
     */
    SERVICE(8, "服务节点");

    private final int code;
    private final String desc;

    FlowNodeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static FlowNodeType of(Integer code) {
        if (code == null) {
            return APPROVAL;
        }
        for (FlowNodeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return APPROVAL;
    }
}
