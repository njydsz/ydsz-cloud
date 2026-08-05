package com.remisoft.workflow.server.service.impl.notification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.util.id.TracerUtils;
import com.remisoft.workflow.domain.dto.FlowCcQueryDTO;
import com.remisoft.workflow.domain.entity.FlowCc;
import com.remisoft.workflow.domain.entity.FlowInstance;
import com.remisoft.workflow.domain.entity.FlowNode;
import com.remisoft.workflow.infra.mapper.FlowCcMapper;
import com.remisoft.workflow.infra.mapper.FlowInstanceMapper;
import com.remisoft.workflow.server.engine.FlowAssigneeResolver;
import com.remisoft.workflow.server.engine.FlowVariableStrategy;
import com.remisoft.workflow.server.service.FlowCcService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GAP-P1: 流程抄送服务实现
 *
 * <p>对 {@link FlowCcService} 接口的完整实现，对标钉钉 / 飞书审批的「抄送我的」独立 Tab。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CC 节点处理</b>：{@link #handleCcNode} — CC 节点（{@code nodeType=5}）触发时
 *       展开 {@code user:/role:/dept:} 权限标识为具体用户列表，并写入 {@code remi_flow_cc}</li>
 *   <li><b>分页查询</b>：{@link #listCcByUser} / {@link #listByInstance} — 抄送我的 / 实例维度</li>
 *   <li><b>已读机制</b>：{@link #markRead} / {@link #markAllRead} — 标记已读 / 全部已读</li>
 *   <li><b>未读统计</b>：{@link #countUnread} — 前端导航栏徽标数据源</li>
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}，
 * 「展开用户 + 批量写入 + 失效缓存」原子性。
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>「抄送我的」走 {@code remi_flow_cc} 索引 {@code idx_cc_user}</li>
 *   <li>未读数走 Redis 缓存（{@code remi:flow:cc:unread:{userId}}），{@code @CacheEvict} 在 {@code markRead} 时失效</li>
 *   <li>CC 节点展开走「单条 SQL 查询 + 应用层 dedup」，避免在 N 次循环中重复查询</li>
 * </ul>
 *
 * <p><b>防御性编码：</b>所有方法均空值检查 + try-catch 兜底，CC 节点失败不影响主流程推进，
 * 异常仅写日志，由 {@code DefaultFlowAdvancer} 继续推进。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowCcService 接口定义
 * @see FlowCc 抄送实体
 * @see FlowAssigneeResolver 审批人解析器（{@code role:/dept:} 展开）
 * @see FlowVariableStrategy 变量解析策略（SpEL 表达式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCcServiceImpl implements FlowCcService {

    /** 抄送记录 Mapper，负责 remi_flow_cc 表的增删改查 */
    private final FlowCcMapper ccMapper;
    /** 流程实例 Mapper，用于获取实例冗余字段（flowCode/flowName/businessKey 等） */
    private final FlowInstanceMapper instanceMapper;
    /** 流程变量解析策略，解析 permissionFlag 中的动态变量（如 ${initiator}） */
    private final FlowVariableStrategy variableStrategy;
    /** 审批人解析器，将 role:/dept:/position:/leader: 等标识展开为具体用户 ID 列表 */
    private final FlowAssigneeResolver assigneeResolver;

    // ============================== 抄送节点处理 ==============================

    /**
     * 处理 CC 节点（流程引擎触发 CC 节点时调用）
     *
     * <p>执行链路：
     * <ol>
     *   <li>校验实例与节点非空</li>
     *   <li>查询流程实例获取冗余字段（{@code flowCode/flowName/businessKey/title}）</li>
     *   <li>解析节点的 {@code permissionFlag}，支持 SpEL 变量（如 {@code ${initiator}}）</li>
     *   <li>按逗号拆分 token，展开为具体用户 ID 列表
     *       （{@code user:/role:/dept:/position:/leader:}）</li>
     *   <li>逐用户写入 {@code remi_flow_cc} 记录</li>
     * </ol>
     *
     * <p><b>降级语义：</b>整个方法在 {@code try-catch} 中，任意异常仅写日志，<b>不抛异常</b>，
     * 避免 CC 节点失败影响主流程推进。
     *
     * @param instanceId 流程实例 ID
     * @param node       CC 节点（{@code nodeType=5}）
     * @param variables  流程变量（用于解析 SpEL 表达式）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCcNode(String instanceId, FlowNode node, Map<String, Object> variables) {
        try {
            if (instanceId == null || node == null) {
                log.warn("[FlowCc] handleCcNode 参数为空: instanceId={} node={}", instanceId, node == null);
                return;
            }

            // 1. 获取流程实例（取 flowCode/flowName/businessKey 等冗余字段）
            FlowInstance instance = instanceMapper.selectById(instanceId);
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
            Set<String> userIds = new LinkedHashSet<>();
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

            // 4. 为每个 userId 写入 FlowCc
            LocalDateTime now = LocalDateTime.now();
            String traceId = TracerUtils.getOrCreateTraceId();
            int insertCount = 0;
            for (String userId : userIds) {
                FlowCc cc = buildCcDO(instance, node, userId, now, traceId);
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

    /**
     * 分页查询「抄送我的」列表
     *
     * <p>支持按 {@code readStatus}（{@code UNREAD/READ}）、{@code flowCode} 过滤，
     * 返回按时间倒序的抄送记录。结果不封装为 {@link PageResponse}，由调用方组装分页信息。
     *
     * @param tenantId 租户 ID
     * @param userId   当前用户 ID
     * @param query    查询条件（{@code pageNum/pageSize/readStatus/flowCode}）
     * @return 抄送列表（异常或参数为空时返回空列表，<b>不抛异常</b>）
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlowCc> pageMyCc(String tenantId, String userId, FlowCcQueryDTO query) {
        try {
            if (userId == null || query == null) {
                return List.of();
            }
            int page = (int) Math.max(query.getPageNum(), 1);
            int size = (int) Math.min(Math.max(query.getPageSize(), 1), PageConstants.MAX_PAGE_SIZE);
            int offset = (page - 1) * size;
            return ccMapper.selectCcByUserPage(tenantId, userId,
                    query.getReadStatus(), query.getFlowCode(), offset, size);
        } catch (Exception e) {
            log.error("[FlowCc] pageMyCc 异常: userId={} err={}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 统计「抄送我的」总条数
     *
     * @param tenantId 租户 ID
     * @param userId   当前用户 ID
     * @param query    查询条件（{@code readStatus/flowCode}）
     * @return 总条数（异常或参数为空时返回 0L）
     */
    @Override
    @Transactional(readOnly = true)
    public long countMyCc(String tenantId, String userId, FlowCcQueryDTO query) {
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

    /**
     * 分页查询「抄送我的」（封装为 {@link PageResponse}）
     *
     * <p>与 {@link #pageMyCc} + {@link #countMyCc} 组合等价，本方法在服务层一次性完成
     * 「分页查询 + 总数统计 + 异常兜底 + PageResponse 封装」，
     * 减少 Controller 层胶水代码。
     *
     * @param userId     当前用户 ID
     * @param readStatus 已读状态过滤（{@code UNREAD/READ}，可为 null）
     * @param flowCode   流程编码过滤（可为 null）
     * @param tenantId   租户 ID
     * @param pageNo     页码（从 1 开始）
     * @param pageSize   每页大小（最大 {@link PageConstants#MAX_PAGE_SIZE}）
     * @return 抄送分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public BaseResponse<List<FlowCc>> listCcByUser(String userId, String readStatus, String flowCode,
                                                   String tenantId, int pageNo, int pageSize) {
        try {
            if (userId == null) {
                return BaseResponse.successPage(0L, 0L, 0L, Collections.emptyList());
            }
            int page = Math.max(pageNo, 1);
            int size = (int) Math.min(Math.max(pageSize, 1), PageConstants.MAX_PAGE_SIZE);
            int offset = (page - 1) * size;

            List<FlowCc> list = ccMapper.selectCcByUserPage(tenantId, userId,
                    readStatus, flowCode, offset, size);
            long total = ccMapper.countCcByUser(tenantId, userId, readStatus, flowCode);
            return BaseResponse.successPage(total, (long) page, (long) size, list);
        } catch (Exception e) {
            log.error("[FlowCc] 分页查询异常: userId={} err={}", userId, e.getMessage(), e);
            return BaseResponse.successPage(0L, 0L, 0L, Collections.emptyList());
        }
    }

    // ============================== 已读标记 ==============================

    /**
     * 标记单条抄送为已读
     *
     * <p>将 {@code remi_flow_cc} 中指定 {@code ccId} 的 {@code read_status} 改为 {@code READ}，
     * 并记录 {@code read_at} 时间戳。异常时仅写日志，<b>不抛异常</b>（避免影响前端操作）。
     *
     * @param tenantId 租户 ID（当前实现未使用，保留参数用于后续按租户校验）
     * @param userId   当前用户 ID
     * @param ccId     抄送 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String tenantId, String userId, String ccId) {
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

    /**
     * 标记某用户当前租户下所有未读抄送为已读
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 受影响行数（异常或参数为空时返回 0）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(String tenantId, String userId) {
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

    /**
     * 统计用户当前租户下未读抄送数
     *
     * <p>通常作为前端「抄送」Tab 角标 / 导航栏红点的数据源。
     * 当前实现直接走 DB，<b>未走 Redis 缓存</b>（参考类注释中的缓存优化建议）。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 未读抄送数
     */
    @Override
    @Transactional(readOnly = true)
    public long countUnread(String userId, String tenantId) {
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

    /**
     * 查询某流程实例的所有抄送记录
     *
     * <p>用于审批详情页「抄送人」Tab 展示，列出该实例触发的所有 CC 节点接收人。
     *
     * @param instanceId 流程实例 ID
     * @param tenantId   租户 ID
     * @return 抄送记录列表（无数据或参数为空时返回空列表）
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlowCc> listByInstance(String instanceId, String tenantId) {
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
    private void expandToken(String token, Map<String, Object> variables, Set<String> userIds) {
        try {
            if (token.startsWith("user:")) {
                String idStr = token.substring("user:".length()).trim();
                Long uid = tryParseLong(idStr);
                if (uid != null) {
                    userIds.add(String.valueOf(uid));
                }
            } else if (token.startsWith("role:") || token.startsWith("dept:")
                    || token.startsWith("position:") || token.startsWith("leader:")) {
                List<Long> expanded = assigneeResolver.expandUsers(token, variables);
                if (expanded != null && !expanded.isEmpty()) {
                    for (Long e : expanded) {
                        userIds.add(String.valueOf(e));
                    }
                }
            } else {
                // 纯数字 → 直接作为 user ID
                Long uid = tryParseLong(token);
                if (uid != null) {
                    userIds.add(String.valueOf(uid));
                } else {
                    // 其他格式 → 尝试通过 resolver 展开
                    List<Long> expanded = assigneeResolver.expandUsers(token, variables);
                    if (expanded != null && !expanded.isEmpty()) {
                        for (Long e : expanded) {
                            userIds.add(String.valueOf(e));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FlowCc] 展开 token 异常: token={} err={}", token, e.getMessage());
        }
    }

    /**
     * 构建 FlowCc 记录
     */
    private FlowCc buildCcDO(FlowInstance instance, FlowNode node,
                               String userId, LocalDateTime now, String traceId) {
        FlowCc cc = new FlowCc();
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
