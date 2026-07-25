package com.njydsz.common.batch.job;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.njydsz.common.batch.enums.BatchStatus;
import com.njydsz.common.batch.enums.ExitStatus;
import com.njydsz.common.batch.listener.JobListener;
import com.njydsz.common.batch.model.BatchExecutionContext;
import com.njydsz.common.batch.step.Step;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 批处理作业（Job）
 *
 * <p>Job 由多个 Step 按顺序组成，支持重启。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * Job job = Job.builder("userImport")
 *     .start(step1)
 *     .next(step2)
 *     .listener(myJobListener)
 *     .build();
 *
 * JobExecution execution = jobLauncher.run(job);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Getter
public class Job {

    private final String name;
    private final List<Step<?, ?>> steps;
    private final List<JobListener> listeners;
    private final boolean restartable;

    private Job(Builder b) {
        this.name = b.name;
        this.steps = new ArrayList<>(b.steps);
        this.listeners = b.listeners == null ? new ArrayList<>() : new ArrayList<>(b.listeners);
        this.restartable = b.restartable;
    }

    /**
     * 执行 Job
     */
    public BatchExecutionContext execute() {
        BatchExecutionContext execution = BatchExecutionContext.builder()
                .name(name)
                .jobInstanceId(UUID.randomUUID().toString())
                .status(BatchStatus.STARTING)
                .startTime(java.time.Instant.now())
                .build();
        notifyBeforeJob(execution);
        try {
            for (Step<?, ?> step : steps) {
                if (execution.getStatus() == BatchStatus.STOPPING
                        || execution.getStatus() == BatchStatus.ABANDONED) {
                    break;
                }
                log.info("Job[{}] executing step: {}", name, step.getName());
                BatchExecutionContext stepExec = step.execute();
                execution.setReadCount(execution.getReadCount() + stepExec.getReadCount());
                execution.setProcessCount(execution.getProcessCount() + stepExec.getProcessCount());
                execution.setWriteCount(execution.getWriteCount() + stepExec.getWriteCount());
                execution.setSkipCount(execution.getSkipCount() + stepExec.getSkipCount());
                if (stepExec.getStatus() == BatchStatus.FAILED) {
                    execution.setStatus(BatchStatus.FAILED);
                    execution.setExitStatus(ExitStatus.FAILED);
                    execution.setErrorMessage("Step failed: " + step.getName());
                    execution.setException(stepExec.getException());
                    break;
                }
            }
            if (execution.getStatus() == BatchStatus.STARTING
                    || execution.getStatus() == BatchStatus.STARTED) {
                execution.setStatus(BatchStatus.COMPLETED);
                execution.setExitStatus(ExitStatus.COMPLETED);
            }
        } catch (Exception ex) {
            log.error("Job[{}] failed", name, ex);
            execution.setStatus(BatchStatus.FAILED);
            execution.setExitStatus(ExitStatus.FAILED);
            execution.setErrorMessage(ex.getMessage());
            execution.setException(ex);
        } finally {
            execution.setEndTime(java.time.Instant.now());
            notifyAfterJob(execution);
        }
        return execution;
    }

    private void notifyBeforeJob(BatchExecutionContext ctx) {
        for (JobListener listener : listeners) {
            try {
                listener.beforeJob(ctx);
            } catch (Exception ex) {
                log.warn("Job listener beforeJob failed", ex);
            }
        }
    }

    private void notifyAfterJob(BatchExecutionContext ctx) {
        for (JobListener listener : listeners) {
            try {
                listener.afterJob(ctx);
            } catch (Exception ex) {
                log.warn("Job listener afterJob failed", ex);
            }
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<Step<?, ?>> steps = new ArrayList<>();
        private List<JobListener> listeners;
        private boolean restartable = true;

        private Builder(String name) {
            this.name = name;
        }

        public Builder start(Step<?, ?> step) {
            if (steps.isEmpty()) {
                steps.add(step);
            } else {
                steps.set(0, step);
            }
            return this;
        }

        public Builder next(Step<?, ?> step) {
            steps.add(step);
            return this;
        }

        public Builder listener(JobListener listener) {
            if (this.listeners == null) {
                this.listeners = new ArrayList<>();
            }
            this.listeners.add(listener);
            return this;
        }

        public Builder restartable(boolean restartable) {
            this.restartable = restartable;
            return this;
        }

        public Job build() {
            if (name == null) {
                throw new IllegalArgumentException("job name must not be null");
            }
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("job must have at least one step");
            }
            return new Job(this);
        }
    }
}
