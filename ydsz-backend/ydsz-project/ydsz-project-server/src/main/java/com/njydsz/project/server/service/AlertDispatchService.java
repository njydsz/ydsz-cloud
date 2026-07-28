package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.alert.AlertDispatch;
/**
 * 告警分发 Service
 *
 * <p>管理告警分发（{@code ydsz_alert_dispatch}）的订阅、推送、确认。</p>
 * <p>告警是项目/系统异常的信号通知，按订阅规则推送给责任人（IM/邮件/短信），</p>
 * <p>支持告警确认/转派/关闭等生命周期管理。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>告警订阅：按规则订阅告警（如 EVM CPI<0.8 触发）</b></li>
 *   <li><b>告警推送：多通道推送(IM/邮件/短信)</b></li>
 *   <li><b>告警处理：确认/转派/关闭</b></li>
 * </ul>
 *
 * <p><b>告警级别：</b>CRITICAL / MAJOR / MINOR / INFO。
 * <p><b>推送通道：</b>企业微信 / 邮件 / 短信 / 系统站内信。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.alert.AlertDispatch 告警实体
 * @see EvmMeasureService EVM Service(EVM 异常触发告警)
 * @see ExecutionRiskService 风险 Service(高风险触发告警)
 */
public interface AlertDispatchService {
    AlertDispatch getById(String id);
    IPage<AlertDispatch> page(int pageNum, int pageSize);
    boolean save(AlertDispatch entity);
    boolean updateById(AlertDispatch entity);
    boolean removeById(String id);
}
