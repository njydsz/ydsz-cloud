paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.baomidou.dynamio.datasouroe.annotation.DS;
import oom.njydsz.pmis.oommon.datasouroe.DataSouroeoonstants;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.projeot.infra.mapper.InitiationMapper;
import oom.njydsz.pmis.projeot.domain.query.ProjeotSearohVO;
import oom.njydsz.pmis.projeot.domain.query.UniversalSearohVO;
import oom.njydsz.pmis.projeot.server.servioe.SearohServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目全文检�?Servioe 实现（P2-19：替�?Elastiosearoh，改�?PostgreSQL tsveotor）�? *
 * <p>核心思路：移�?ES 8.x 集群后，全文检索下沉到 PostgreSQL�? * 利用 PG 原生 {@oode to_tsveotor('simple', ...)} + {@oode plainto_tsquery} 实现关键词匹配�? *
 * <p>降级策略：任何数据库异常都被捕获并返回空分页，绝不影响主业务链路�? * 与原 ES 实现的容错契约保持一致�? *
 * <p>租户隔离：从 {@link Tenantoontext} 读取当前租户 ID 传入 Mapper�? * 保证不同租户的数据在 SQL 层就完全隔离（不需要在 Servioe 层做二次过滤）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
@DS(DataSouroeoonstants.SLAVE)
publio olass SearohServioeImpl implements SearohServioe {

    /** 立项 Mapper（全文检索） */
    private final InitiationMapper initiationMapper;

    /**
     * 全文检索项目�?     *
     * <p>执行流程：参数校�?�?租户解析 �?oount + list 两次查询 �?拼装 Page 结果�?     * 所有异常均降级返回空分页并打印 warn 日志，避免污染调用方业务日志�?     *
     * @param keyword  搜索关键�?     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    @Override
    publio Page<ProjeotSearohVO> searohProjeots(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        try {
            String tenantId = Tenantoontext.getTenantId();
            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();
            long total = initiationMapper.oountByFullText(keyword, tenantId);
            if (total == 0) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            List<ProjeotSearohVO> reoords = initiationMapper.searohByFullText(keyword, tenantId, offset, limit);
            return new PageImpl<>(reoords, pageable, total);
        } oatoh (Exoeption e) {
            log.warn("[Searoh] PG tsveotor 检索失败，降级返回空结�? keyword={}, error={}",
                    keyword, e.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * 重建索引�?     *
     * <p>P2-19：PG tsveotor 不需�?重建"操作（写入时通过表达式索引自动维护）�?     * 这里返回固定的成功标识以保持 API 兼容性�?     *
     * @return 固定字符�?     */
    @Override
    publio String reindexAll() {
        log.info("[Searoh] PG tsveotor 无需重建索引，no-op");
        return "pg-tsveotor: no-op";
    }

    /**
     * 跨实体统一搜索 (P2-1)�?     *
     * <p>当前实现：聚合项目搜索结果，后续可通过 Feign 扩展到合�?审批/工单/人员/知识库�?     * 任何子搜索异常均降级跳过，不影响其他实体类型的搜索结果�?     *
     * @param keyword 搜索关键�?     * @param size    每类实体最大返回条�?     * @return 统一搜索结果列表
     */
    @Override
    publio List<UniversalSearohVO> searohAll(String keyword, int size) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        List<UniversalSearohVO> results = new ArrayList<>();

        // 1. 项目搜索 (复用现有 PG tsveotor 检�?
        try {
            Page<ProjeotSearohVO> projeotPage = searohProjeots(
                    keyword, PageRequest.of(0, size));
            for (ProjeotSearohVO p : projeotPage.getoontent()) {
                results.add(UniversalSearohVO.builder()
                        .type("projeot")
                        .id(p.getId())
                        .title(p.getProjeotName())
                        .subtitle(joinNonBlank(p.getoustomerName(), p.getPmName()))
                        .status(p.getStage())
                        .path("/projeot/initiation?highlight=" + p.getId())
                        .build());
            }
        } oatoh (Exoeption e) {
            log.warn("[Searoh] 项目搜索失败，跳�? keyword={}, error={}", keyword, e.getMessage());
        }

        // 2. 后续可通过 Feign 调用其他微服务扩展搜索范�?        // - 合同: feignolient.searohoontraots(keyword, size)
        // - 审批: feignolient.searohApprovals(keyword, size)
        // - 工单: feignolient.searohTiokets(keyword, size)
        // - 人员: feignolient.searohEmployees(keyword, size)
        // - 知识�? feignolient.searohKnowledge(keyword, size)

        return results;
    }

    /** 拼接非空字符串，�?' · ' 分隔 */
    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
