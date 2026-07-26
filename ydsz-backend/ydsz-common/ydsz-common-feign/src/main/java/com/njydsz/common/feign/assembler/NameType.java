package com.njydsz.common.feign.assembler;

/**
 * 跨服务名称解析的实体类型。
 *
 * <p>用于 {@link NameAssembler} 在批量富化时路由到对应的 Internal API。
 * 每个枚举值对应 ydsz-userinfo 服务的一个 batch-names 端点：
 *
 * <ul>
 *   <li>{@link #USER} → {@code POST /api/internal/user/batch-names}（id → realName）</li>
 *   <li>{@link #DEPT} → {@code POST /api/internal/dept/batch-names}（id → deptName）</li>
 *   <li>{@link #ROLE} → {@code POST /api/internal/role/batch-names}（id → roleName）</li>
 *   <li>{@link #POST} → {@code POST /api/internal/post/batch-names}（id → postName）</li>
 *   <li>{@link #COMPANY} → {@code POST /api/internal/company/batch-names}（id → companyName）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum NameType {

    /** 用户 ID → 真实姓名（realName） */
    USER,

    /** 部门 ID → 部门名（deptName） */
    DEPT,

    /** 角色 ID → 角色名（roleName） */
    ROLE,

    /** 岗位 ID → 岗位名（postName） */
    POST,

    /** 公司 ID → 公司名（companyName） */
    COMPANY
}
