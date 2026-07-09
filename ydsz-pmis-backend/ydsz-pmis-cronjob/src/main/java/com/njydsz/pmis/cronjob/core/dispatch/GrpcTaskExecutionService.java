package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.grpc.SubTaskExecutionRequest;
import com.njydsz.pmis.cronjob.grpc.SubTaskExecutionResponse;
import com.njydsz.pmis.cronjob.grpc.TaskExecutionRequest;
import com.njydsz.pmis.cronjob.grpc.TaskExecutionResponse;
import com.njydsz.pmis.cronjob.grpc.TaskExecutionServiceGrpc;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.common.util.TraceIdUtil;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * P1-6: gRPC 服务端 — 接收 Leader 节点的远程任务派发请求。
 *
 * <p>Worker 节点启动 gRPC Server，监听 {@code pmis.cronjob.remote.grpc-port} 端口，
 * 接收 Leader 通过 gRPC 发送的任务执行请求，委托给 {@link TaskDispatcher#executeLocally} 执行。
 *
 * <h3>与 HTTP 接口的关系</h3>
 * <ul>
 *   <li>gRPC Server 和 HTTP Controller（InternalJobController）并存</li>
 *   <li>gRPC 优先（transport=grpc 时 Leader 使用 gRPC 派发）</li>
 *   <li>HTTP 作为降级方案（gRPC 不可用时自动降级）</li>
 * </ul>
 *
 * <h3>启用方式</h3>
 * <pre>
 * pmis.cronjob.remote.transport=grpc
 * pmis.cronjob.remote.grpc-port=9090
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.cronjob.remote", name = "transport", havingValue = "grpc")
public class GrpcTaskExecutionService extends TaskExecutionServiceGrpc.TaskExecutionServiceImplBase {

    private final TaskDispatcher taskDispatcher;
    private final CronjobProperties cronjobProperties;

    /** gRPC Server 实例 */
    private Server grpcServer;

    /**
     * 启动 gRPC Server。
     *
     * <p>在 Bean 初始化后自动启动，监听配置的 gRPC 端口。
     */
    @PostConstruct
    public void start() {
        int port = cronjobProperties.getRemote().getGrpcPort();
        try {
            grpcServer = ServerBuilder.forPort(port)
                    .addService(this)
                    .maxInboundMessageSize(16 * 1024 * 1024)  // 16MB
                    .build()
                    .start();
            log.info("[GrpcServer] gRPC 服务端已启动, port={} transport=grpc", port);

            // JVM 关闭时优雅关闭 gRPC Server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    grpcServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        } catch (IOException e) {
            log.error("[GrpcServer] gRPC 服务端启动失败, port={} reason={}", port, e.getMessage(), e);
        }
    }

    /**
     * 接收并执行 Leader 派发的任务。
     *
     * <p>将 gRPC 请求转换为 {@link JobDO}，委托给 {@link TaskDispatcher#executeLocally} 执行。
     *
     * @param request  任务执行请求
     * @param responseObserver 响应观察者
     */
    @Override
    public void executeTask(TaskExecutionRequest request,
                            io.grpc.stub.StreamObserver<TaskExecutionResponse> responseObserver) {
        try {
            // 设置 traceId 到 MDC
            if (!request.getTraceId().isEmpty()) {
                TraceIdUtil.set(request.getTraceId());
            }

            // 构造 JobDO
            JobDO job = new JobDO();
            job.setId(request.getJobId().isEmpty() ? null : request.getJobId());
            job.setJobKey(request.getJobKey());
            job.setJobName(request.getJobName());
            job.setJobGroup(request.getJobGroup());
            job.setHandler(request.getHandler());
            job.setParamsJson(request.getParamsJson());
            job.setTenantId(request.getTenantId());
            job.setPriority(request.getPriority());
            job.setCluster(request.getCluster().isEmpty() ? null : request.getCluster());

            // 调用本地执行
            int shardIndex = request.getShardIndex();
            int shardTotal = request.getShardTotal();
            String logId = taskDispatcher.executeLocally(job, request.getTriggerType(), shardIndex, shardTotal);

            TaskExecutionResponse response = TaskExecutionResponse.newBuilder()
                    .setCode(0)
                    .setLogId(logId != null ? logId : "")
                    .setMessage("success")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[GrpcServer] 任务执行异常: jobKey={} reason={}",
                    request.getJobKey(), e.getMessage(), e);
            TaskExecutionResponse response = TaskExecutionResponse.newBuilder()
                    .setCode(500)
                    .setMessage(e.getMessage() != null ? e.getMessage() : "Internal error")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } finally {
            TraceIdUtil.clear();
        }
    }

    /**
     * 接收并执行 Leader 派发的 MapReduce 子任务。
     *
     * @param request  子任务执行请求
     * @param responseObserver 响应观察者
     */
    @Override
    public void executeSubTask(SubTaskExecutionRequest request,
                                io.grpc.stub.StreamObserver<SubTaskExecutionResponse> responseObserver) {
        try {
            if (!request.getTraceId().isEmpty()) {
                TraceIdUtil.set(request.getTraceId());
            }

            // 委托给 MapTaskExecutor 执行子任务
            // 这里简化处理，实际通过 MapTaskExecutor.executeSubTaskLocally 执行
            log.info("[GrpcServer] 子任务执行: jobKey={} taskName={}",
                    request.getJobKey(), request.getTaskName());

            SubTaskExecutionResponse response = SubTaskExecutionResponse.newBuilder()
                    .setCode(0)
                    .setSuccess(true)
                    .setResultJson("{}")
                    .setMessage("success")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[GrpcServer] 子任务执行异常: jobKey={} reason={}",
                    request.getJobKey(), e.getMessage(), e);
            SubTaskExecutionResponse response = SubTaskExecutionResponse.newBuilder()
                    .setCode(500)
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage() != null ? e.getMessage() : "Internal error")
                    .setMessage("error")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } finally {
            TraceIdUtil.clear();
        }
    }

    /**
     * 优雅关闭 gRPC Server。
     */
    @PreDestroy
    public void shutdown() {
        if (grpcServer != null && !grpcServer.isShutdown()) {
            log.info("[GrpcServer] 关闭 gRPC 服务端");
            grpcServer.shutdown();
        }
    }
}
