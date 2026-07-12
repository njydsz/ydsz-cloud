package com.njydsz.pmis.common.security;

import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据权限助手
 *
 * <p>提供业务层手动调用的工具：判定数据是否可访问 / 抛出异常 / 返回 deptId 列表。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public final class DataScopeHelper {

    private DataScopeHelper() {
    }

    /**
     * 当前上下文
     *
     * @return 当前登录用户的数据权限上下文
     */
    public static DataScopeContext current() {
        LoginUser user = AuthContext.getCurrentOrNull();
        return DataScopeContext.from(user);
    }

    /**
     * 校验当前用户是否有权访问指定 dept 数据
     *
     * @param targetDeptId 目标部门 ID
     * @throws BizException 无权限时抛出
     */
    public static void requireDept(String targetDeptId) {
        if (targetDeptId == null) {
            return;
        }
        DataScopeContext ctx = current();
        if (ctx.isAll()) {
            return;
        }
        if (ctx.getDeptId() != null && ctx.getDeptId().equals(targetDeptId)) {
            return;
        }
        if (ctx.getCustomDeptIds() != null && ctx.getCustomDeptIds().contains(targetDeptId)) {
            return;
        }
        throw new BizException(StandardResultCode.DATA_SCOPE_FORBIDDEN, "error.common.msg_e107b337");
    }

    /**
     * 校验当前用户是否有权访问指定用户数据
     *
     * @param ownerUserId 目标用户 ID
     * @throws BizException 无权限时抛出
     */
    public static void requireOwner(String ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        DataScopeContext ctx = current();
        if (ctx.isAll()) {
            return;
        }
        if (ctx.getScope() == DataScope.DEPT || ctx.getScope() == DataScope.DEPT_AND_CHILD) {
            return;
        }
        if (ctx.getUserId() != null && ctx.getUserId().equals(ownerUserId)) {
            return;
        }
        throw new BizException(StandardResultCode.DATA_SCOPE_FORBIDDEN, "error.common.msg_4982e9ba");
    }

    /**
     * 计算 WHERE 条件 SQL 片段（不含 WHERE 关键字）
     *
     * <p>使用默认字段名: dept_id / created_by
     *
     * @param deptAlias 部门字段别名
     * @param userAlias 创建人字段别名
     * @return 条件片段，无数据权限时返回 ""
     */
    public static String buildSqlFragment(String deptAlias, String userAlias) {
        return buildSqlFragment(deptAlias, userAlias, "dept_id", "created_by");
    }

    /**
     * 计算 WHERE 条件 SQL 片段（含自定义字段名）
     *
     * <p>由 Service 层拼接到 QueryWrapper：
     * <pre>
     *   String fragment = DataScopeHelper.buildSqlFragment("t", "t", "dept_id", "created_by");
     *   if (!fragment.isEmpty()) wrapper.apply(fragment);
     * </pre>
     *
     * @param deptAlias  部门字段表别名
     * @param userAlias  创建人字段表别名
     * @param deptColumn 部门字段列名 (如 "dept_id", "business_dept_id")
     * @param userColumn 创建人字段列名 (如 "created_by", "creator_id")
     * @return 条件片段，无数据权限时返回 ""
     */
    public static String buildSqlFragment(String deptAlias, String userAlias,
                                          String deptColumn, String userColumn) {
        DataScopeContext ctx = current();
        if (ctx.isAll()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        switch (ctx.getScope()) {
            case SELF -> sb.append(prefix(deptAlias)).append(userColumn).append(suffix(userAlias))
                    .append(" = ").append(safeValue(ctx.getUserId()));
            case DEPT -> sb.append(prefix(deptAlias)).append(deptColumn).append(suffix(deptAlias))
                    .append(" = ").append(safeValue(ctx.getDeptId()));
            case DEPT_AND_CHILD -> {
                List<String> ids = ctx.getDeptIds();
                if (ids == null || ids.isEmpty()) {
                    sb.append(prefix(deptAlias)).append(deptColumn).append(suffix(deptAlias))
                            .append(" = ").append(safeValue(ctx.getDeptId()));
                } else {
                    sb.append(prefix(deptAlias)).append(deptColumn).append(suffix(deptAlias))
                            .append(" IN (").append(joinIds(ids)).append(")");
                }
            }
            case CUSTOM -> {
                List<String> ids = ctx.getCustomDeptIds();
                if (ids == null || ids.isEmpty()) {
                    sb.append("1=0");
                } else {
                    sb.append(prefix(deptAlias)).append(deptColumn).append(suffix(deptAlias))
                            .append(" IN (").append(joinIds(ids)).append(")");
                }
            }
            case PROJECT -> {
                // 项目级数据权限: 走"用户可访问的项目ID集合"子查询。
                // 当前实现: 保守默认 1=0, 避免越权; 业务层在调用前需自行使用
                // ProjectPermissionHelper.projIdsIn(userId) 拼接 IN 条件并传入。
                log.debug("[DataScope] PROJECT scope 默认返回 1=0, 业务层需自行注入项目成员子查询");
                sb.append("1=0 /* PROJECT scope: 由业务层显式注入 */");
            }
            default -> log.debug("[DataScope] 未处理 scope={}", ctx.getScope());
        }
        return sb.toString();
    }

    private static String prefix(String alias) {
        return (alias == null || alias.isEmpty()) ? "" : alias + ".";
    }

    private static String suffix(String alias) {
        return alias == null ? "" : "";
    }

    private static String safeValue(Object v) {
        return v == null ? "NULL" : v.toString();
    }

    private static String joinIds(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            if (!first) sb.append(",");
            // P3-1：雪花字符串 ID 在 SQL 中需加引号
            sb.append("'").append(id.replace("'", "''")).append("'");
            first = false;
        }
        return sb.length() == 0 ? "NULL" : sb.toString();
    }

    /**
     * 过滤可访问部门 ID 集合（去除越权 ID）
     *
     * <p>P3-1：部门 ID 已统一为雪花字符串，参数与返回值改为 {@code Set<String> / List<String>}。
     *
     * @param candidate 候选部门 ID 集合
     * @return 当前用户可访问的部门 ID 列表
     */
    public static List<String> filterDeptIds(Set<String> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return new ArrayList<>();
        }
        DataScopeContext ctx = current();
        if (ctx.isAll()) {
            return new ArrayList<>(candidate);
        }
        List<String> allowed = new ArrayList<>();
        for (String id : candidate) {
            if (id == null || id.isEmpty()) continue;
            if (ctx.getDeptId() != null && ctx.getDeptId().equals(id)) {
                allowed.add(id);
                continue;
            }
            if (ctx.getCustomDeptIds() != null && ctx.getCustomDeptIds().contains(id)) {
                allowed.add(id);
            }
        }
        return allowed;
    }
}
