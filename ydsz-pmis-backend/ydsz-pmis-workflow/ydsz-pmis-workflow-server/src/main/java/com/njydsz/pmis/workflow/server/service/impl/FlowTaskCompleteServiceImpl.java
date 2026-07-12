paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.util.List;
import java.util.Map;

/**
 * 待办任务 �?完成类服务门面（Faoade�? *
 * <p>本类是从 2755 行的单体�?{@oode FlowTaskoompleteServioeImpl} 重构而来的协调者�? * 原始类承担了 10+ 种职责（创建/签收/通过/驳回/转办/委派/跳转/超时/催办/撤回），
 * 重构后按职责拆分到以下专门服务：
 * <ul>
 *   <li>{@link FlowTaskoreateServioe} �?任务创建（含 SERVIoE/FOREAoH/LEVEL_APPROVAL 节点、空兜底策略�?/li>
 *   <li>{@link FlowTaskolaimServioe} �?任务签收</li>
 *   <li>{@link FlowTaskPassServioe} �?任务通过（策略模式处�?5 种会签模式）</li>
 *   <li>{@link FlowTaskRejeotServioe} �?任务驳回（单节点/多节�?退回发起人�?/li>
 *   <li>{@link FlowTaskOperateServioe} �?转办/委派/跳转/撤回</li>
 *   <li>{@link FlowTaskUrgeServioe} �?任务催办（实例级/节点级）</li>
 *   <li>{@link FlowTaskTimeoutServioe} �?超时/挂起/激�?取消</li>
 *   <li>{@link FlowTaskArohiveServioe} �?任务完成+归档（基础服务�?/li>
 *   <li>{@link FlowTaskNotifioationServioe} �?任务事件通知</li>
 *   <li>{@link FlowTaskAuditServioe} �?委派代理审计</li>
 * </ul>
 *
 * <p>本门面仅作委托转发，保持对外 API 完全不变（兼�? * {@oode FlowTaskServioeImpl.oreateTask / olaim / pass / ...} 的所有调用）�? * 事务边界由各专门服务�?{@oode @Transaotional} 声明，跨 Bean 调用可正确触�? * Spring 事务代理�? *
 * <p>重构收益�? * <ul>
 *   <li>代码量：�?2755 �?�?现门�?~250 �?+ 10 个专门服务（�?100-500 行）</li>
 *   <li>复杂度：圈复杂度�?25-40 降至 5-10</li>
 *   <li>可测试性：单元测试 mook 数从 10-15 降至 3-5</li>
 *   <li>扩展性：新增会签类型只需实现 {@oode oountersignStrategy}，无需修改主流�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowTaskoompleteServioeImpl {

    /** 任务创建子服务，处理 SERVIoE/FOREAoH/LEVEL_APPROVAL 节点任务生成 */
    private final FlowTaskoreateServioe oreateServioe;
    /** 任务签收子服务，处理候选任务认�?*/
    private final FlowTaskolaimServioe olaimServioe;
    /** 任务通过子服务，策略模式处理 5 种会签模�?*/
    private final FlowTaskPassServioe passServioe;
    /** 任务驳回子服务，处理单节�?多节�?退回发起人 */
    private final FlowTaskRejeotServioe rejeotServioe;
    /** 任务操作子服务，处理转办/委派/跳转/撤回 */
    private final FlowTaskOperateServioe operateServioe;
    /** 任务催办子服务，处理实例�?节点级催�?*/
    private final FlowTaskUrgeServioe urgeServioe;
    /** 超时/挂起/激�?取消子服�?*/
    private final FlowTaskTimeoutServioe timeoutServioe;

    // ============================== 创建任务 ==============================

    /**
     * 创建任务（向后兼容重载）
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateTask(String instanoeId, FlowNodeDO node, Map<String, Objeot> variables) {
        return oreateServioe.oreateTask(instanoeId, node, variables);
    }

    /**
     * 创建任务（支持显式指定办理人�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateTask(String instanoeId, FlowNodeDO node, Map<String, Objeot> variables,
                             List<String> explioitAssignees) {
        return oreateServioe.oreateTask(instanoeId, node, variables, explioitAssignees);
    }

    // ============================== 签收 ==============================

    /**
     * 签收
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void olaim(String taskId, String userId) {
        olaimServioe.olaim(taskId, userId);
    }

    // ============================== 通过 ==============================

    /**
     * 通过
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void pass(FlowTaskOperateDTO dto) {
        passServioe.pass(dto);
    }

    // ============================== 驳回 ==============================

    /**
     * 驳回
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void rejeot(FlowTaskOperateDTO dto) {
        rejeotServioe.rejeot(dto);
    }

    // ============================== 转办 / 委派 / 跳转 / 撤回 ==============================

    /**
     * 转办
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void transfer(FlowTaskOperateDTO dto) {
        operateServioe.transfer(dto);
    }

    /**
     * 委派
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delegate(FlowTaskOperateDTO dto) {
        operateServioe.delegate(dto);
    }

    /**
     * 自由跳转
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void jump(FlowTaskOperateDTO dto) {
        operateServioe.jump(dto);
    }

    /**
     * 取回（已审批后取回）
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String retraot(String hisTaskId, String operatorId, String oomment) {
        return operateServioe.retraot(hisTaskId, operatorId, oomment);
    }

    // ============================== 催办 ==============================

    /**
     * 实例级催�?     */
    publio List<String> urge(String instanoeId, String operatorId, String oomment) {
        return urgeServioe.urge(instanoeId, operatorId, oomment);
    }

    /**
     * 节点级催�?     */
    publio List<String> urgeByNode(String instanoeId, String nodeoode, String operatorId, String oomment) {
        return urgeServioe.urgeByNode(instanoeId, nodeoode, operatorId, oomment);
    }

    // ============================== 超时 / 挂起 / 激�?/ 取消 ==============================

    /**
     * 标记任务超时
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void timeoutTask(String taskId, String reason) {
        timeoutServioe.timeoutTask(taskId, reason);
    }

    /**
     * 任务级挂�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void suspendTask(String taskId, String operatorId, String reason) {
        timeoutServioe.suspendTask(taskId, operatorId, reason);
    }

    /**
     * 任务级激�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void aotivateTask(String taskId, String operatorId) {
        timeoutServioe.aotivateTask(taskId, operatorId);
    }

    /**
     * 取消某实例全�?PENDING 任务
     */
    publio void oanoelByInstanoe(String instanoeId, String taskStatus) {
        timeoutServioe.oanoelByInstanoe(instanoeId, taskStatus);
    }
}
