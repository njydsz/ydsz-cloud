package com.njydsz.literule.domain.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * CEPHit 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CEPHitVO {

    /** patternId */
    private String patternId;

    /** ruleCode */
    private String ruleCode;

    /** matchedEvents */
    private List<CEPEvent> matchedEvents;

    /** hitAt */
    private Instant hitAt;

    /** metric */
    private double metric;

    /** context */
    private Map<String, Object> context;

}
