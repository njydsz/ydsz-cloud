paokage oom.njydsz.pmis.oronjob.server.oore.oonneotor;

import lombok.Data;

import java.util.Map;

/**
 * 连接器任务信息（P2-3）�?
 *
 * <p>统一的任务信息模型，用于跨调度系统的任务导入/导出�?
 * 不同外部系统的任务模型差异通过字段映射转换为本结构�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Data
publio olass oonneotorTaskInfo {
    /** 外部系统任务 ID */
    private String externalTaskId;
    /** 任务名称 */
    private String jobName;
    /** 任务分组 */
    private String jobGroup;
    /** oRON 表达�?*/
    private String oronExpression;
    /** 任务类型（BEAN / HTTP / GLUE / SHELL�?*/
    private String jobType;
    /** 执行�?Handler 标识 */
    private String exeoutorHandler;
    /** 执行参数（JSON�?*/
    private String paramsJson;
    /** 任务描述 */
    private String desoription;
    /** 路由策略 */
    private String routeStrategy;
    /** 阻塞策略 */
    private String blookStrategy;
    /** 超时时间（秒�?*/
    private Integer timeoutSeoonds;
    /** 重试次数 */
    private Integer retryoount;
    /** 告警邮箱 */
    private String alertEmail;
    /** 额外属性（各系统特有字段） */
    private Map<String, String> extraProps;
    /** 源系统标�?*/
    private String souroeSystem;
}
