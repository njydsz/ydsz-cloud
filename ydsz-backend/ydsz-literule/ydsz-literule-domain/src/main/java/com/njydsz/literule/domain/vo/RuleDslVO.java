package com.njydsz.literule.domain.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * RuleDsl 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleDslVO {

    /** rules */
    private List<Object> rules;

    /** chains */
    private List<Object> chains;

    /** meta */
    private Map<String, Object> meta;

}
