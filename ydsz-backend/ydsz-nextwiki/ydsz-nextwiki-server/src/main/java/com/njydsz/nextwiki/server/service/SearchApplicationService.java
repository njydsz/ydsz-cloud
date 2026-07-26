package com.njydsz.nextwiki.server.service;

import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索应用服务
 * <p>
 * 编排文件搜索与索引重建操作，协调领域服务。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchApplicationService {

    private final SearchDomainService searchDomainService;

    public SearchResultVO search(String keyword, String userId, String scope,
                                 int page, int pageSize) {
        return searchDomainService.search(keyword, userId, scope, page, pageSize);
    }

    public void rebuildAllIndices() {
        searchDomainService.rebuildAllIndices();
    }
}
