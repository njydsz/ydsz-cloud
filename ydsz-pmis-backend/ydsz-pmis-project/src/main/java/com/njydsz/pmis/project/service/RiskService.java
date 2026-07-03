package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.RiskCreateDTO;
import com.njydsz.pmis.project.dto.RiskStatusDTO;
import com.njydsz.pmis.project.entity.RiskDO;

import java.util.List;
import java.util.Map;

/**
 * 项目风险服务
 *
 * <p>提供项目风险的登记、状态变更、查询与聚合统计能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RiskService {

    /**
     * 登记风险
     *
     * @param dto 风险创建参数
     * @return 风险ID
     */
    Long create(RiskCreateDTO dto);

    /**
     * 变更风险状态
     *
     * @param dto 状态变更参数
     */
    void changeStatus(RiskStatusDTO dto);

    /**
     * 删除风险
     *
     * @param id 风险ID
     */
    void delete(Long id);

    /**
     * 根据ID查询风险
     *
     * @param id 风险ID
     * @return 风险实体
     */
    RiskDO getById(Long id);

    /**
     * 分页查询风险
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param riskLevel    风险等级
     * @param initiationId 项目立项ID
     * @return 分页结果
     */
    Page<RiskDO> page(int page, int size, String keyword, String status,
                      String riskLevel, Long initiationId);

    /**
     * 查询项目下所有风险
     *
     * @param initiationId 项目立项ID
     * @return 风险列表
     */
    List<RiskDO> listByInitiation(Long initiationId);

    /**
     * 风险等级分布统计
     *
     * @param initiationId 项目立项ID
     * @return 各等级风险数量列表
     */
    List<Map<String, Object>> aggregateByLevel(Long initiationId);
}
