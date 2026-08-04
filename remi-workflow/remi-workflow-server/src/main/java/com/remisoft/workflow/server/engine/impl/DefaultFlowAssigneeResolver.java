package com.remisoft.workflow.server.engine.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.remisoft.workflow.server.engine.FlowAssigneeResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认办理人解析器（空实现）
 *
 * <p>当业务模块未提供 FlowAssigneeResolver Bean 时使用本兜底实现。
 * 不展开 ROLE/DEPT/LEADER/POSITION，assigneeId 原样保留。
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
@Component
@ConditionalOnMissingBean(FlowAssigneeResolver.class)
public class DefaultFlowAssigneeResolver implements FlowAssigneeResolver {

    @Override
    public List<Long> expandUsers(String permissionFlag, Map<String, Object> variables) {
        log.debug("[Flow] 未提供 FlowAssigneeResolver 实现，ROLE/DEPT/LEADER/POSITION 不展开: {}",
                permissionFlag);
        return Collections.emptyList();
    }
}
