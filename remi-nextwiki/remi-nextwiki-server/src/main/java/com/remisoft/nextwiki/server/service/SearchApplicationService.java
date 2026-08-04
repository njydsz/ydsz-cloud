package com.remisoft.nextwiki.server.service;

import org.springframework.stereotype.Service;

import com.remisoft.nextwiki.domain.service.SearchDomainService;
import com.remisoft.nextwiki.domain.vo.SearchResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 搜索应用服务。
 * <p>整合 ES 提供全文检索能力。
 *
 * @author remi-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class SearchApplicationService {

    private final SearchDomainService searchDomainService;

    /**
     * 全文检索（按关键词在用户可见范围内分页搜索）。
     * <p>权限由底层 {@link SearchDomainService} 结合用户作用域过滤（非管理员仅搜自己创建的文件）。
     *
     * @param keyword 搜索关键词（文件名/路径/全文/标签）
     * @param userId  操作人 ID（用于权限与结果过滤）
     * @param scope   搜索作用域（如 "all"/"my"，由领域服务解释）
     * @param page    页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页搜索结果 {@link SearchResultVO}
     * @complexity O(query)（一次搜索引擎查询 + 分页）
     * @note 只读，无事务边界；委托 {@link SearchDomainService} 实现
     */
    public SearchResultVO search(String keyword, String userId, String scope,
                                 int page, int pageSize) {
        return searchDomainService.search(keyword, userId, scope, page, pageSize);
    }

    /**
     * 重建全量搜索索引（通常由定时任务或运维操作触发）。
     *
     * @return 无返回值
     * @complexity O(N)（N 为文件总数，遍历重新建索引，耗时较长）
     * @note 非事务（批量操作）；执行期间建议避开高峰期，避免影响在线搜索
     * @see com.remisoft.nextwiki.server.job.NextwikiScheduledJobs#rebuildSearchIndex()
     */
    public void rebuildAllIndices() {
        searchDomainService.rebuildAllIndices();
    }
}
