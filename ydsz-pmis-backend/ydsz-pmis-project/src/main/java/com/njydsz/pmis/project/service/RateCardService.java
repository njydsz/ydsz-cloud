package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.RateCardCreateDTO;
import com.njydsz.pmis.project.entity.RateCardDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 对外报价费率服务
 *
 * <p>按 (职级 × 项目类型 × 客户等级) 三维度管理对外报价费率，支持 3 级回退匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RateCardService {

    /**
     * 创建对外报价费率
     *
     * @param dto 费率创建参数
     * @return 费率ID
     */
    String create(RateCardCreateDTO dto);

    /**
     * 更新费率
     *
     * @param id  费率ID
     * @param dto 费率更新参数
     */
    void update(String id, RateCardCreateDTO dto);

    /**
     * 删除费率
     *
     * @param id 费率ID
     */
    void delete(String id);

    /**
     * 根据ID查询费率
     *
     * @param id 费率ID
     * @return 费率实体
     */
    RateCardDO getById(String id);

    /** 按职级+项目类型+客户等级 命中当前生效的费率 */
    RateCardDO matchEffective(String levelCode, String projectType, String customerLevel, LocalDate date);

    /**
     * 按职级列出费率
     *
     * @param levelCode 职级编码
     * @return 费率列表
     */
    List<RateCardDO> listByLevel(String levelCode);

    /**
     * 分页查询费率
     *
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @param levelCode 职级编码
     * @param status    状态过滤
     * @return 分页结果
     */
    Page<RateCardDO> page(int page, int size, String levelCode, String status);
}
