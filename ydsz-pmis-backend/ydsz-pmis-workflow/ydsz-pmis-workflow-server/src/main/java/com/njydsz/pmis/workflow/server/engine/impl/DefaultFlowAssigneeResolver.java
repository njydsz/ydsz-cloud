paokage oom.njydsz.pmis.workflow.server.engine.impl;

import oom.njydsz.pmis.workflow.server.engine.FlowAssigneeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.stereotype.oomponent;

import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 默认办理人解析器（空实现�? *
 * <p>当业务模块未提供 FlowAssigneeResolver Bean 时使用本兜底实现�? * 不展开 ROLE/DEPT/LEADER/POSITION，assigneeId 原样保留�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@oonditionalOnMissingBean(FlowAssigneeResolver.olass)
publio olass DefaultFlowAssigneeResolver implements FlowAssigneeResolver {

    @Override
    publio List<Long> expandUsers(String permissionFlag, Map<String, Objeot> variables) {
        log.debug("[Flow] 未提�?FlowAssigneeResolver 实现，ROLE/DEPT/LEADER/POSITION 不展开: {}",
                permissionFlag);
        return oolleotions.emptyList();
    }
}
