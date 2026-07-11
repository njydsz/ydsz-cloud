package com.njydsz.pmis.project.server.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.domain.dto.WarrantyCreateDTO;
import com.njydsz.pmis.project.domain.dto.WarrantyTerminateDTO;
import com.njydsz.pmis.project.domain.entity.WarrantyDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 质保期服务
 *
 * <p>项目结项后自动创建质保期，到期前 N 天提醒，到期后自动 EXPIRED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface WarrantyService {

    /**
     * 创建质保期（结项审批通过后自动调用）
     */
    String create(WarrantyCreateDTO dto);

    /**
     * 手动提前终止质保期
     */
    void terminate(WarrantyTerminateDTO dto);

    /**
     * 扫描即将到期（≤ today + N 天）并标记 EXPIRING_SOON
     */
    int scanExpiring(LocalDate today, int noticeDays);

    /**
     * 扫描已过期（end_date < today）并标记 EXPIRED
     */
    int scanOverdue(LocalDate today);

    /**
     * 即将到期（用于定时通知）
     */
    List<WarrantyDO> listExpiring(LocalDate until);

    /**
     * 分页查询
     */
    Page<WarrantyDO> page(int page, int size, String status, String initiationId, String keyword);

    /**
     * 详情
     */
    WarrantyDO getById(String id);
}
