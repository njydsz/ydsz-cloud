package com.remisoft.nextwiki.server.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.web.health.AbstractModuleHealthIndicator;
import com.remisoft.common.file.storage.IFileStorageProvider;
import com.remisoft.nextwiki.domain.repository.FileNodeRepository;
import com.remisoft.nextwiki.server.metrics.NextwikiMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 健康检查。
 *
 * <p>职责：报告存储可用性、数据库连接状态。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class NextwikiHealthIndicator extends AbstractModuleHealthIndicator {

    private final FileNodeRepository fileNodeRepository;
    private final NextwikiMetrics nextwikiMetrics;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 设置文件存储提供者（由 @Bean 注册时注入）
     */
    public void setFileStorageProvider(IFileStorageProvider fileStorageProvider) {
        this.fileStorageProvider = fileStorageProvider;
    }

    /**
     * 转发文件上传埋点到 {@link NextwikiMetrics#recordUpload()}。
     *
     * <p>本类对外保留一组 {@code recordXxx} 方法，是为兼容早期只注入
     * 健康检查器的调用方；新代码应直接依赖 {@link NextwikiMetrics}，
     * 避免把指标采集职责与健康检查耦合在一起。
     */
    public void recordUpload() {
        nextwikiMetrics.recordUpload();
    }

    /**
     * 转发文件下载埋点到 {@link NextwikiMetrics#recordDownload()}。
     *
     * <p>兼容性门面方法，不参与健康检查判定，新代码请直接使用 {@link NextwikiMetrics}。
     */
    public void recordDownload() {
        nextwikiMetrics.recordDownload();
    }

    /**
     * 转发文件删除埋点到 {@link NextwikiMetrics#recordDelete()}。
     *
     * <p>兼容性门面方法，不参与健康检查判定，新代码请直接使用 {@link NextwikiMetrics}。
     */
    public void recordDelete() {
        nextwikiMetrics.recordDelete();
    }

    /**
     * 转发分享创建埋点到 {@link NextwikiMetrics#recordShare()}。
     *
     * <p>兼容性门面方法，不参与健康检查判定，新代码请直接使用 {@link NextwikiMetrics}。
     */
    public void recordShare() {
        nextwikiMetrics.recordShare();
    }

    /**
     * 转发搜索请求埋点到 {@link NextwikiMetrics#recordSearch()}。
     *
     * <p>兼容性门面方法，不参与健康检查判定，新代码请直接使用 {@link NextwikiMetrics}。
     */
    public void recordSearch() {
        nextwikiMetrics.recordSearch();
    }

    /**
     * 转发预览生成埋点到 {@link NextwikiMetrics#recordPreview()}。
     *
     * <p>兼容性门面方法，不参与健康检查判定，新代码请直接使用 {@link NextwikiMetrics}。
     */
    public void recordPreview() {
        nextwikiMetrics.recordPreview();
    }

    /**
     * 汇总 NextWiki 的存储与数据库探针结果。
     *
     * <p><b>判定约定：</b>文件存储未配置时<b>不</b>将实例判为 DOWN，仅追加
     * {@code warning} 明细。原因是知识库的浏览、检索、权限等只读能力不依赖对象存储，
     * 若因此摘除实例会放大故障面；真正致命的是数据库不可用，该项由
     * {@code checkTableProbe} 决定最终健康态。
     *
     * <p>数据库探针使用固定的哨兵用户 {@code "health-check"} 执行计数查询，
     * 该用户预期无数据，返回 0 属正常，只要不抛异常即视为连接健康。
     *
     * @param builder 健康状态构建器，本方法只追加明细，最终状态由父类裁定
     */
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        boolean storageAvailable = fileStorageProvider != null;
        builder.withDetail("storageAvailable", storageAvailable);

        // 数据库探针
        checkTableProbe(builder, "databaseConnected", () -> fileNodeRepository.countByUser("health-check"));

        if (!storageAvailable) {
            builder.withDetail("warning", "文件存储未配置，上传功能不可用");
        }
    }
}
