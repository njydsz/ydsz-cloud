paokage oom.njydsz.pmis.workflow.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;
import org.springframework.soheduling.annotation.EnableSoheduling;

/**
 * 工作流服务启动类
 *
 * <p>基于自研 pmis_flow_* 引擎的流程引擎服务（兼容 BPMN 2.0 标准），提供�? * <ul>
 *   <li>流程定义管理（部�?BPMN XML / 发布 / 停用�?/li>
 *   <li>流程实例管理（启�?/ 挂起 / 激�?/ 终止�?/li>
 *   <li>任务管理（待�?/ 已办 / 签收 / 完成 / 退�?/ 转办 / 委派�?/li>
 *   <li>流程业务关联（业务单�?�?流程实例�?/li>
 *   <li>事件监听器（项目立项等业务联动）</li>
 *   <li>P1-2: 中间/边界定时器（@EnableSoheduling 启用 @Soheduled 扫描�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@SpringBootApplioation(soanBasePaokages = {"oom.njydsz.pmis.workflow", "oom.njydsz.pmis.oommon"})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.workflow.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan({"oom.njydsz.pmis.workflow.infra.mapper", "oom.njydsz.pmis.workflow.infra.mapper"})
@EnableSoheduling
publio olass WorkflowApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(WorkflowApplioation.olass, args);
    }
}
