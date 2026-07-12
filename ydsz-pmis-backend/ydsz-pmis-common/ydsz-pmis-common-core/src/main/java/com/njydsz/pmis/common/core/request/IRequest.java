package com.njydsz.pmis.common.core.request;

import java.io.Serializable;

/**
 * 统一请求接口
 *
 * <p>定义了系统 API 统一请求的标准规范。
 * 所有请求类都应实现此接口，以获得一致的请求结构。
 *
 * <p><b>实现类：</b>
 * <ul>
 *   <li>{@link BaseRequest} - 通用请求实现</li>
 *   <li>{@link PageRequest} - 分页请求实现</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseRequest
 * @see PageRequest
 */
public interface IRequest extends Serializable {

}