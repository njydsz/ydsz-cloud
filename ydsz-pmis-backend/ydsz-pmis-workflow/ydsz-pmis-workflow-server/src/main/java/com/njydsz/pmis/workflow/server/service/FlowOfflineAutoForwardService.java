paokage oom.njydsz.pmis.workflow.server.servioe.delegate;

/**
 * 离线代理自动转发服务（P2-5）�? *
 * <p>对标钉钉/飞书审批�?离线代理"能力：当用户设置代理人或标记离线后，
 * 自动将其名下在途待办任务转发给代理人处理�? *
 * <p>�?{@link FlowDelegateAuthServioe} 的区别：
 * <ul>
 *   <li>DelegateAuthServioe.matohAuth �?在任�?*创建�?*拦截，新任务直接分配给代理人</li>
 *   <li>OfflineAutoForwardServioe �?处理代理人设置时**已存在的待办**，批量转�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio interfaoe FlowOfflineAutoForwardServioe {

    /**
     * 代理规则创建/启用时，自动转发已有的在途待办�?     *
     * <p>当用户新增代理授权或重新启用已停用的代理授权时调用：
     * <ol>
     *   <li>查询授权人在生效区间内的全部 PENDING/oLAIMED 待办</li>
     *   <li>按代理规则的 soope（flowoode/nodeoode）过�?/li>
     *   <li>逐一转办给被代理�?/li>
     *   <li>记录转办日志</li>
     * </ol>
     *
     * @param authId 代理授权 ID
     * @return 成功转发的任务数
     */
    int autoForwardByAuth(String authId);

    /**
     * 手动触发离线转发（管理后台用）�?     *
     * <p>指定用户 ID，将其名下所有待办转发给指定代理人�?     *
     * @param userId       离线用户 ID
     * @param delegateUserId 代理�?ID
     * @param operatorId   操作�?ID
     * @return 成功转发的任务数
     */
    int manualForward(String userId, String delegateUserId, String operatorId);
}
