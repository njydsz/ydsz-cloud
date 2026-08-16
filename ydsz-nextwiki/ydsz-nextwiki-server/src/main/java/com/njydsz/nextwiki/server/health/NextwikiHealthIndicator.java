package com.njydsz.nextwiki.server.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

/**
 * NextWiki 健康检查。
 *
 * <p>职责：报告存储可用性、数据库连接状态。
 *
 * <p>健康检查与指标采集职责分离：所有业务指标（upload/download/delete/...）
 * 由 {@code NextwikiMetrics} 通过 {@link com.njydsz.common.sentry.adapter.SentryMetricsAdapter} 体系承载，
 * 本指标器不持有指标转发门面，违反云顶编码规范"业务模块优先使用 common 模块能力，
 * 不重复造轮子，不承担非自身职责"原则。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class NextwikiHealthIndicator extends AbstractModuleHealthIndicator {

    private final FileNodeRepository fileNodeRepository;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 设置文件存储提供者（由 @Bean 注册时注入）
     */
    public void setFileStorageProvider(IFileStorageProvider fileStorageProvider) {
        this.fileStorageProvider = fileStorageProvider;
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
