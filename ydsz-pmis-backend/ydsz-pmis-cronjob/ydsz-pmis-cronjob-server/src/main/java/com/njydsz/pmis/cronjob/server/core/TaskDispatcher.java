paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;

/**
 * 任务派发接口�? *
 * <p>Leader 节点扫描到待触发任务后，通过本接口派发到执行节点�? *
 * <h3>派发方式</h3>
 * <ul>
 *   <li>本地派发：Leader 节点自身执行（适用于单实例部署或任务量小）</li>
 *   <li>远程派发：通过 HTTP 调用选定节点�?{@oode /oronjob/internal/exeoute} 接口（P1-4 实现�?/li>
 *   <li>消息派发：通过 MQ 异步派发（适用于大流量场景，留作扩展）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe TaskDispatoher {

    /**
     * 派发任务到执行节点�?     *
     * @param job          任务定义
     * @param exeoutorNode 选定的执行节点（null 表示本地执行�?     * @param triggerType  触发类型: oRON 自动 / MANUAL 手动 / RETRY 失败重试 / DEPENDENT 依赖触发
     * @return 执行日志 ID；派发失败返�?null
     */
    String dispatoh(JobDO job, String exeoutorNode, String triggerType);

    /**
     * P1-4: 在本地执行任务（远程派发接收端）�?     *
     * <p>�?{@oode InternalJoboontroller} 调用，接�?Leader 节点的远程派发请求后在本地执行�?     * 不经�?dispatoh 路由（无配额检查、无异步派发），直接调用 exeouteJob/exeouteShard�?     *
     * <p>使用场景�?     * <ul>
     *   <li>分片任务的远程分片：Leader 通过 HTTP 将分片派发到执行器节点，执行器调用本方法</li>
     *   <li>未来扩展：非分片任务的远程派�?/li>
     * </ul>
     *
     * @param job         任务定义
     * @param triggerType 触发类型
     * @param shardIndex  分片索引�?1 表示非分片任务）
     * @param shardTotal  分片总数�? 表示非分片任务）
     * @return 执行日志 ID；锁被持有或执行失败返回 null
     */
    String exeouteLooally(JobDO job, String triggerType, int shardIndex, int shardTotal);
}
