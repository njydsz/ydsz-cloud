package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.ops.OpsTicket;
/**
 * 运维工单 Service
 *
 * <p>管理运维工单（{@code ydsz_ops_ticket}）的创建、分派、处理、关闭。</p>
 * <p>运维工单是项目交付后客户对系统/服务的报修/咨询/请求入口，</p>
 * <p>由客服/运维团队响应，按 SLA 跟踪处理进度。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>工单分派：按产品线/区域/技能分派给处理人</b></li>
 *   <li><b>SLA 跟踪：超时自动升级处理</b></li>
 *   <li><b>满意度回访：关闭后触发客户评价</b></li>
 * </ul>
 *
 * <p><b>工单类型：</b>故障 / 咨询 / 请求 / 投诉 / 建议。
 * <p><b>优先级：</b>P0(紧急) / P1(高) / P2(中) / P3(低)。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.ops.OpsTicket 运维工单实体
 * @see SatisfactionService 满意度 Service(工单关闭后触发评价)
 */
public interface OpsTicketService {
    OpsTicket getById(String id);
    IPage<OpsTicket> page(int pageNum, int pageSize);
    boolean save(OpsTicket entity);
    boolean updateById(OpsTicket entity);
    boolean removeById(String id);
}
