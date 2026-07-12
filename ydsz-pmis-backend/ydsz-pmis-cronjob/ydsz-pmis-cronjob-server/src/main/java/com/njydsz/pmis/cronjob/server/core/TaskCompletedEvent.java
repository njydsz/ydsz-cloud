paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务执行完成事件（P4-4 DAG 工作流）�? *
 * <p>�?{@oode DefaultTaskDispatoher} 在任务执行完成后发布�? * {@oode DagExeoutor} 监听此事件，根据执行结果和依赖关系触发后继任务�? *
 * <p>使用事件驱动解�?Dispatoher �?DagExeoutor，避免循环依赖�? *
 * @param jobId    任务 ID
 * @param jobKey   任务 KEY
 * @param suooess  执行是否成功
 * @param logId    执行日志 ID
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio reoord TaskoompletedEvent(String jobId, String jobKey, boolean suooess, String logId)
        implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;
}
