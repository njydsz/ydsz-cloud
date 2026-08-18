package com.njydsz.message.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgUserChannelDO;

/**
 * 用户通道绑定 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_user_channel</code>。
 *
 * <p>通道绑定是消息发送的最终地址（手机号/邮箱/IM openId 等），按渠道类型 + 用户 ID 唯一。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_channel — (用户+渠道类型) 唯一索引
 *   <li>idx_channel_account — 渠道账号查询索引（用于回执回调反查用户）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.config.MsgUserChannel 通道绑定实体
 * @see com.njydsz.message.server.service.MsgUserChannelService 通道绑定 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgUserChannelMapper extends BaseMapper<MsgUserChannelDO> {}
