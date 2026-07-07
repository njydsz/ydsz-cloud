package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.RateInternalCreateDTO;
import com.njydsz.pmis.project.entity.RateInternalDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 对内成本费率服务
 *
 * <p>按 (职级 × 事业部) 维度管理内部核算成本费率，支持 (level+dept) 优先匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RateInternalService {

    /**
     * 创建对内成本费率
     *
     * @param dto 费率创建参数
     * @return 费率ID
     */
    Long create(RateInternalCreateDTO dto);

    /**
     * 更新费率
     *
     * @param id  费率ID
     * @param dto 费率更新参数
     */
    void update(String id, RateInternalCreateDTO dto);

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
    RateInternalDO getById(String id);

    /** 命中当前生效的对内成本费率 */
    RateInternalDO matchEffective(String levelCode, Long departmentId, LocalDate date);

    /**
     * 按职级+部门列出费率
     *
     * @param levelCode    职级编码
     * @param departmentId 部门ID
     * @return 费率列表
     */
    List<RateInternalDO> listByLevelAndDept(String levelCode, Long departmentId);

    /**
     * 分页查询费率
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param levelCode    职级编码
     * @param departmentId 部门ID
     * @param status       状态过滤
     * @return 分页结果
     */
    Page<RateInternalDO> page(int page, int size, String levelCode, Long departmentId, String status);
}
