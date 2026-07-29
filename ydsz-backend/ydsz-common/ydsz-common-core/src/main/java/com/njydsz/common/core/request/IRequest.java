package com.njydsz.common.core.request;

import java.io.Serializable;

/**
 * 统一请求标记接口
 *
 * <p>定义了系统 API 统一请求的标准规范。
 * 所有请求类可实现此接口以获得一致的请求结构标识。
 *
 * <p>当前为预留接口，业务模块主要使用 {@code com.njydsz.common.domain.query.PageQuery}
 * 作为分页查询基类。如需 HTTP API 层的请求封装，可实现此接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface IRequest extends Serializable {

}
