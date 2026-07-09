package com.njydsz.pmis.userinfo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.userinfo.dto.OutsourceRateCreateDTO;
import com.njydsz.pmis.userinfo.dto.OutsourceRateUpdateDTO;
import com.njydsz.pmis.userinfo.entity.OutsourceRateDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 外包职级费率服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OutsourceRateService {

    /**
     * 创建外包职级费率
     *
     * @param dto 创建参数
     * @return 新建记录 ID
     */
    String create(OutsourceRateCreateDTO dto);

    /**
     * 更新外包职级费率
     *
     * @param id  记录 ID
     * @param dto 更新参数
     */
    void update(String id, OutsourceRateUpdateDTO dto);

    /**
     * 删除外包职级费率（逻辑删除）
     *
     * @param id 记录 ID
     */
    void delete(String id);

    /**
     * 按 ID 查询详情
     *
     * @param id 记录 ID
     * @return 外包职级费率记录
     */
    OutsourceRateDO getById(String id);

    /**
     * 分页查询
     *
     * @param page    当前页（从 1 开始）
     * @param size    每页大小
     * @param keyword 关键字（匹配级别编码/名称）
     * @param segment 级别段位
     * @param status  状态
     * @return 分页结果
     */
    Page<OutsourceRateDO> page(int page, int size, String keyword, String segment, String status);

    /**
     * 按级别编码 + 日期匹配生效中的费率（按版本号倒序取最新）
     *
     * @param rateCode 级别编码
     * @param date     生效日期（为空时取当前日期）
     * @return 生效费率记录，未找到返回 null
     */
    OutsourceRateDO matchEffective(String rateCode, LocalDate date);

    /**
     * 查询某日期生效中的所有外包费率
     *
     * @param date 生效日期（为空时取当前日期）
     * @return 生效费率列表
     */
    List<OutsourceRateDO> listEffective(LocalDate date);
}
