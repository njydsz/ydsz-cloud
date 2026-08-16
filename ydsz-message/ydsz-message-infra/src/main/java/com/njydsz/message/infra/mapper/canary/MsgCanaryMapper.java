package com.njydsz.message.infra.mapper.canary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.domain.entity.canary.MsgCanary;

/**
 * 消息灰度桶 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_canary</code>。
 *
 * <p>灰度桶定义按用户 ID 哈希/百分位的灰度受众，模板/渠道灰度发布时按用户命中桶决定是否启用。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_canary_key — (模板/渠道+版本) 唯一索引
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.canary.MsgCanary 灰度桶实体
 * @see com.njydsz.message.server.service.MsgCanaryService 灰度桶 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgCanaryMapper extends BaseMapper<MsgCanary> {}
