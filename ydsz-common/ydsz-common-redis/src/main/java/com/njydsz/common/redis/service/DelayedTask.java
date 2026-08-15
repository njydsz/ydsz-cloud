package com.njydsz.common.redis.service;

import java.io.Serializable;
import java.util.Objects;

/**
 * 延迟任务实体接口。
 * <p>表示一个可被延迟队列调度的任务项。
 *
 * <p><b>迁移说明：</b>自 v1.1.0 起标记废弃，计划 v2.0.0 移除。
 * 当前无业务消费方。如需延迟队列能力，推荐使用消息中间件（如 RabbitMQ 死信队列、RocketMQ 定时消息）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 v1.1.0 起无消费方，计划 v2.0.0 移除。替代方案：消息中间件延迟消息。
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public class DelayedTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private String queueName;
    private transient Object payload;
    private long executeAt;
    private int retryCount;

    public DelayedTask() {
    }

    public DelayedTask(String taskId, String queueName, Object payload, long executeAt, int retryCount) {
        this.taskId = taskId;
        this.queueName = queueName;
        this.payload = payload;
        this.executeAt = executeAt;
        this.retryCount = retryCount;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public long getExecuteAt() {
        return executeAt;
    }

    public void setExecuteAt(long executeAt) {
        this.executeAt = executeAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DelayedTask)) {
            return false;
        }
        DelayedTask that = (DelayedTask) o;
        return Objects.equals(taskId, that.taskId) && Objects.equals(queueName, that.queueName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, queueName);
    }

    @Override
    public String toString() {
        return "DelayedTask{" +
                "taskId='" + taskId + '\'' +
                ", queueName='" + queueName + '\'' +
                ", executeAt=" + executeAt +
                ", retryCount=" + retryCount +
                '}';
    }
}
