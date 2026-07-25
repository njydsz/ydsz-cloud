package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.vo.VariableVO;

/**
 * 系统变量 Service。
 *
 * <p>提供变量 CRUD、按 key 查询值、分页查询等能力，集成 Redis 缓存和缓存穿透防护。
 *
 * @author ydsz-team
 */
public interface VariableService {

    /**
     * 按 ID 查询系统变量。
     *
     * @param id 主键 ID
     * @return 变量 VO
     */
    VariableVO getById(String id);

    /**
     * 按变量键查询变量值（走缓存）。
     *
     * @param variableKey 变量键
     * @return 变量值，不存在返回 null
     */
    String getVariableValue(String variableKey);

    /**
     * 分页查询系统变量（支持搜索过滤）。
     *
     * @param pageNum     当前页码
     * @param pageSize    每页记录数
     * @param variableKey 变量键模糊搜索（可选）
     * @param status      状态过滤（可选）
     * @return 分页结果（VO）
     */
    IPage<VariableVO> page(int pageNum, int pageSize, String variableKey, String status);

    /**
     * 查询全部系统变量（仅内部使用）。
     *
     * @return 变量列表（VO）
     */
    List<VariableVO> list();

    /**
     * 创建系统变量。
     *
     * @param dto 变量 DTO
     * @return 主键 ID
     */
    String save(VariableDTO dto);

    /**
     * 更新系统变量。
     *
     * @param dto 变量 DTO
     * @return 是否成功
     */
    boolean updateById(VariableDTO dto);

    /**
     * 删除系统变量（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
