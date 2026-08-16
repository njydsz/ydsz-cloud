package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.domain.entity.UserLoginHistory;

/**
 * 用户登录历史 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_user_login_history}，提供登录历史记录的 CRUD 操作。
 *
 * <p><b>主要查询场景：</b>
 *
 * <ul>
 *   <li>查询用户最近登录记录
 *   <li>查询某 IP 的登录失败次数（IP 封禁判断）
 *   <li>定期清理历史数据
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserLoginHistoryMapper extends BaseMapper<UserLoginHistory> {}
