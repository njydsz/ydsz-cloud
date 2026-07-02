package com.njydsz.pmis.execution.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目全文检索 Repository。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Repository
public interface ProjectSearchRepository extends ElasticsearchRepository<ProjectSearchDoc, String> {

    /**
     * 按项目名称、客户名称或合同名称查找文档。
     *
     * @param projectName  项目名称
     * @param customerName 客户名称
     * @param contractName 合同名称
     * @return 匹配的文档列表
     */
    List<ProjectSearchDoc> findByProjectNameOrCustomerNameOrContractName(
            String projectName, String customerName, String contractName);
}
