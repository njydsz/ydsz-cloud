package com.njydsz.pmis.common.redis.service;

import java.io.Serializable;
import java.util.Objects;

/**
 * 分布式延时队列任务
 *
 * <p>存储在 ZSET 中，score 为到期时间戳（毫秒），member 为任务实例。
 * 通过 {@link Object#equals} 和 {@link Object#hashCode} 区分不同任务，
 * 避免反序列化时不同实例的 member 重复。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
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
        if (this == o) return true;
        if (!(o instanceof DelayedTask)) return false;
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
