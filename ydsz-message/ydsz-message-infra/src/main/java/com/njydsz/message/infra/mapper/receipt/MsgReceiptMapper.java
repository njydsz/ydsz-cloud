package com.njydsz.message.infra.mapper.receipt;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgReceipt;

/**
 * 消息回执 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_receipt</code>。
 *
 * <p>回执由 {@code ReceiptPuller} 主动拉取或渠道回调写入，与 {@code ydsz_msg_log} 一对多关联。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_msg_id_channel — (消息+渠道) 唯一索引
 *   <li>idx_receipt_at — 回执时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.entity.receipt.MsgReceipt 回执实体
 * @see com.njydsz.message.server.service.MsgReceiptService 回执 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgReceiptMapper extends BaseMapper<MsgReceipt> {}
