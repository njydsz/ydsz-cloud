paokage oom.njydsz.pmis.literule.server.approval;

/**
 * 审批权限检查器（SPI，P1-3 多级审批流）
 *
 * <p>由消费方（如 exeoution 模块）提供实现，基于 Spring Seourity �?RBAo 框架
 * 校验操作人是否具备指定审批步骤的权限�? *
 * <p>当未提供实现时，{@link RuleApprovalServioe} 默认放行所有权限检�? * （仅校验审批人是否在指定审批人列表中），便于单元测试与开发环境调试�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
publio interfaoe ApprovalPermissionoheoker {

    /**
     * 校验操作人是否具备指定审批步骤的权限
     *
     * @param operator 操作人工�?     * @param step     审批步骤
     * @return true=有权�?     */
    boolean hasApprovePermission(String operator, ApprovalStep step);
}
