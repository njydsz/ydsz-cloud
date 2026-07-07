package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.workflow.dto.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowCcDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.mapper.FlowCcMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.service.FlowCcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP-P1: 流程抄送服务实现
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #handleCcNode} — CC 节点触发时展开接收人（user:/role:/dept:）并批量写入 pmis_flow_cc</li>
 *   <li>{@link #listCcByUser} — "抄送我的"分页查询，返回统一 {@link PageResult}</li>
 *   <li>{@link #markRead} / {@link #markAllRead} — 已读标记</li>
 *   <li>{@link #countUnread} — 未读数（前端导航栏徽标）</li>
 *   <li>{@link #listByInstance} — 实例维度抄送列表</li>
 * </ul>
 *
 * <p>所有方法均防御性编码：空值检查 + try-catch，保证不拖垮主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCcServiceImpl implements FlowCcService {

    private final FlowCcMapper ccMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowVariableStrategy variableStrategy;
    private final FlowAssigneeResolver assigneeResolver;

    // ============================== 抄送节点处理 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCcNode(Long instanceId, FlowNodeDO node, Map<String, Object> variables) {
        try {
            if (instanceId == null || node == null) {
                log.warn("[FlowCc] handleCcNode 参数为空: instanceId={} node={}", instanceId, node == null);
                return;
            }

            // 1. 获取流程实例（取 flowCode/flowName/businessKey 等冗余字段）
            FlowInstanceDO instance = instanceMapper.selectById(instanceId);
            if (instance == null) {
                log.warn("[FlowCc] 流程实例不存在: instanceId={}", instanceId);
                return;
            }

            // 2. 解析 permissionFlag
            String permissionFlag = node.getPermissionFlag();
            if (!StringUtils.hasText(permissionFlag)) {
                log.warn("[FlowCc] 抄送节点无 permissionFlag: instanceId={} nodeCode={}",
                        instanceId, node.getNodeCode());
                return;
            }
            String resolved = variableStrategy.resolveAssignee(permissionFlag, variables);
            if (!StringUtils.hasText(resolved)) {
                resolved = permissionFlag;
            }

            // 3. 按逗号拆分，逐个 token 展开用户
            Set<Long> userIds = new LinkedHashSet<>();
            String[] tokens = resolved.split(",");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                expandToken(trimmed, variables, userIds);
            }

            if (userIds.isEmpty()) {
                log.warn("[FlowCc] 抄送节点展开后无接收人: instanceId={} nodeCode={} permissionFlag={}",
                        instanceId, node.getNodeCode(), permissionFlag);
                return;
            }

            // 4. 为每个 userId 写入 FlowCcDO
            LocalDateTime now = LocalDateTime.now();
            String traceId = TraceIdUtil.getOrCreate();
            int insertCount = 0;
            for (Long userId : userIds) {
                FlowCcDO cc = buildCcDO(instance, node, userId, now, traceId);
                ccMapper.insert(cc);
                insertCount++;
            }

            // 5. 日志
            log.info("[FlowCc] 抄送节点处理完成: instanceId={} nodeCode={} ccCount={} traceId={}",
                    instanceId, node.getNodeCode(), insertCount, traceId);
        } catch (Exception e) {
            log.error("[FlowCc] 抄送节点处理异常: instanceId={} nodeCode={} err={}",
                    instanceId, node != null ? node.getNodeCode() : "null", e.getMessage(), e);
        }
    }

    // ============================== 分页查询 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<FlowCcDO> pageMyCc(Long tenantId, Long userId, FlowCcQueryDTO query) {
        try {
            if (userId == null || query == null) {
                return List.of();
            }
            int page = (int) Math.max(query.getPage(), 1);
            int size = (int) Math.min(Math.max(query.getSize(), 1), PageQuery.MAX_SIZE);
            int offset = (page - 1) * size;
            return ccMapper.selectCcByUserPage(tenantId, userId,
                    query.getReadStatus(), query.getFlowCode(), offset, size);
        } catch (Exception e) {
            log.error("[FlowCc] pageMyCc 异常: userId={} err={}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countMyCc(Long tenantId, Long userId, FlowCcQueryDTO query) {
        try {
            if (userId == null || query == null) {
                return 0L;
            }
            return ccMapper.countCcByUser(tenantId, userId, query.getReadStatus(), query.getFlowCode());
        } catch (Exception e) {
            log.error("[FlowCc] countMyCc 异常: userId={} err={}", userId, e.getMessage(), e);
            return 0L;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FlowCcDO> listCcByUser(Long userId, String readStatus, String flowCode,
                                             Long tenantId, int pageNo, int pageSize) {
        try {
            if (userId == null) {
                return PageResult.empty();
            }
            int page = Math.max(pageNo, 1);
            int size = (int) Math.min(Math.max(pageSize, 1), PageQuery.MAX_SIZE);
            int offset = (page - 1) * size;

            List<FlowCcDO> list = ccMapper.selectCcByUserPage(tenantId, userId,
                    readStatus, flowCode, offset, size);
            long total = ccMapper.countCcByUser(tenantId, userId, readStatus, flowCode);
            return PageResult.of(list, total, page, size);
        } catch (Exception e) {
            log.error("[FlowCc] 分页查询异常: userId={} err={}", userId, e.getMessage(), e);
            return PageResult.empty();
        }
    }

    // ============================== 已读标记 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long tenantId, Long userId, Long ccId) {
        try {
            if (ccId == null || userId == null) {
                return;
            }
            int n = ccMapper.markRead(ccId, userId, LocalDateTime.now());
            log.info("[FlowCc] 标记已读: ccId={} userId={} affected={}", ccId, userId, n);
        } catch (Exception e) {
            log.error("[FlowCc] 标记已读异常: ccId={} userId={} err={}", ccId, userId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long tenantId, Long userId) {
        try {
            if (userId == null || tenantId == null) {
                return 0;
            }
            int n = ccMapper.markAllRead(tenantId, userId, LocalDateTime.now());
            log.info("[FlowCc] 全部已读: userId={} tenantId={} affected={}", userId, tenantId, n);
            return n;
        } catch (Exception e) {
            log.error("[FlowCc] 全部已读异常: userId={} tenantId={} err={}",
                    userId, tenantId, e.getMessage(), e);
            return 0;
        }
    }

    // ============================== 未读数 ==============================

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId, Long tenantId) {
        try {
            if (userId == null || tenantId == null) {
                return 0L;
            }
            return ccMapper.countCcUnreadByUser(tenantId, userId);
        } catch (Exception e) {
            log.error("[FlowCc] 未读数查询异常: userId={} tenantId={} err={}",
                    userId, tenantId, e.getMessage(), e);
            return 0L;
        }
    }

    // ============================== 实例抄送列表 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<FlowCcDO> listByInstance(Long instanceId, Long tenantId) {
        try {
            if (instanceId == null) {
                return List.of();
            }
            return ccMapper.selectByInstanceId(tenantId, instanceId);
        } catch (Exception e) {
            log.error("[FlowCc] 实例抄送列表查询异常: instanceId={} tenantId={} err={}",
                    instanceId, tenantId, e.getMessage(), e);
            return List.of();
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 展开单个权限标识 token 为用户 ID 列表
     *
     * <p>支持的格式：
     * <ul>
     *   <li>user:1001 → 直接取 1001</li>
     *   <li>role:hr / dept:10 / position:PM / leader:1001 → 通过 assigneeResolver.expandUsers() 展开</li>
     *   <li>纯数字 → 尝试作为 user ID</li>
     *   <li>其他 → 尝试通过 assigneeResolver 展开</li>
     * </ul>
     */
    private void expandToken(String token, Map<String, Object> variables, Set<Long> userIds) {
        try {
            if (token.startsWith("user:")) {
                String idStr = token.substring("user:".length()).trim();
                Long uid = tryParseLong(idStr);
                if (uid != null) {
                    userIds.add(uid);
                }
            } else if (token.startsWith("role:") || token.startsWith("dept:")
                    || token.startsWith("position:") || token.startsWith("leader:")) {
                List<Long> expanded = assigneeResolver.expandUsers(token, variables);
                if (expanded != null && !expanded.isEmpty()) {
                    userIds.addAll(expanded);
                }
            } else {
                // 纯数字 → 直接作为 user ID
                Long uid = tryParseLong(token);
                if (uid != null) {
                    userIds.add(uid);
                } else {
                    // 其他格式 → 尝试通过 resolver 展开
                    List<Long> expanded = assigneeResolver.expandUsers(token, variables);
                    if (expanded != null && !expanded.isEmpty()) {
                        userIds.addAll(expanded);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FlowCc] 展开 token 异常: token={} err={}", token, e.getMessage());
        }
    }

    /**
     * 构建 FlowCcDO 记录
     */
    private FlowCcDO buildCcDO(FlowInstanceDO instance, FlowNodeDO node,
                               Long userId, LocalDateTime now, String traceId) {
        FlowCcDO cc = new FlowCcDO();
        cc.setTenantId(instance.getTenantId());
        cc.setInstanceId(instance.getId());
        cc.setNodeCode(node.getNodeCode());
        cc.setNodeName(node.getNodeName());
        cc.setFlowCode(instance.getFlowCode());
        cc.setFlowName(instance.getFlowName());
        cc.setBusinessKey(instance.getBusinessId());
        cc.setCcUserId(userId);
        cc.setCcType("CC_NODE");
        cc.setReadStatus("UNREAD");
        cc.setTitle(instance.getTitle());
        cc.setProviderTraceId(traceId);
        cc.setCreatedAt(now);
        cc.setUpdatedAt(now);
        return cc;
    }

    /**
     * 安全解析 Long
     */
    private Long tryParseLong(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            log.warn("[FlowCcServiceImpl] Long 解析失败 str={}: {}", str, e.getMessage());
            return null;
        }
    }
}
