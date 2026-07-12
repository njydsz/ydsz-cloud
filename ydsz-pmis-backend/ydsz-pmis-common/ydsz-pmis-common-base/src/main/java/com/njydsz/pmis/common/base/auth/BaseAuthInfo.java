package com.njydsz.pmis.common.base.auth;

import java.io.Serializable;

/**
 * 认证上下文信息基类（Web/App 共享）
 *
 * <p>定义认证上下文的统一抽象，子类覆盖 {@link #getServiceTypeCode()} 返回具体的服务类型编码
 * （例如 "WEB" 或 "APP"），用于业务层区分请求来源。
 *
 * <p>本类包含完整的认证信息能力，包括：
 * <ul>
 *   <li>用户ID、登录账号、姓名等基础信息</li>
 *   <li>租户ID、公司ID、部门ID、项目ID、区域ID 等多维度隔离信息</li>
 *   <li>Token、刷新Token、过期时间等会话信息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseAuthInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    protected Long userId;

    /** 登录账号 */
    protected String username;

    /** 真实姓名 */
    protected String realName;

    /** 租户ID */
    protected String tenantId;

    /** 公司ID集合（逗号分隔） */
    protected String companyIds;

    /** 部门ID集合（逗号分隔） */
    protected String deptIds;

    /** 项目ID集合（逗号分隔） */
    protected String projectIds;

    /** 区域ID集合（逗号分隔） */
    protected String regionIds;

    /** 访问令牌 */
    protected String accessToken;

    /** 刷新令牌 */
    protected String refreshToken;

    /** 过期时间（时间戳，毫秒） */
    protected Long expireTime;

    /**
     * 获取当前服务类型编码
     *
     * @return 服务类型编码，例如 "WEB" / "APP" / "API"
     */
    public abstract String getServiceTypeCode();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCompanyIds() {
        return companyIds;
    }

    public void setCompanyIds(String companyIds) {
        this.companyIds = companyIds;
    }

    public String getDeptIds() {
        return deptIds;
    }

    public void setDeptIds(String deptIds) {
        this.deptIds = deptIds;
    }

    public String getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(String projectIds) {
        this.projectIds = projectIds;
    }

    public String getRegionIds() {
        return regionIds;
    }

    public void setRegionIds(String regionIds) {
        this.regionIds = regionIds;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }
}
