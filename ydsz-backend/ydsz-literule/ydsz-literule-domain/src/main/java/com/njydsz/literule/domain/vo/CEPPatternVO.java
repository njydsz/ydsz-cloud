package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

import java.time.Duration;

/**
 * CEPPattern 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CEPPatternVO {

    /** id */
    private String id;

    /** type */
    private String type;

    /** ruleCode */
    private String ruleCode;

    /** name */
    private String name;

    /** window */
    private Duration window;

    /** slide */
    private Duration slide;

    /** windowType */
    private String windowType;

    /** sessionGap */
    private Duration sessionGap;

    /** threshold */
    private double threshold;

    /** eventType */
    private String eventType;


    /** filter */
    private String filter;

    /** aggregateFunction */
    private String aggregateFunction;

    /** aggregateField */
    private String aggregateField;

    /** sequence */
    private List<Object> sequence;

    /** description */
    private String description;

    /** order */
    private int order;



    /** minGap */
    private Duration minGap;

    /** maxGap */
    private Duration maxGap;

}
