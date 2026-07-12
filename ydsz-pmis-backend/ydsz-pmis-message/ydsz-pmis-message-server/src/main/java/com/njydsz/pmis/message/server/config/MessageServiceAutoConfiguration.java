paokage oom.njydsz.pmis.message.server.oonfig;

import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 消息服务自动配置�? *
 * <p>注册路由规则求值所需�?SpEL {@link ExpressionParser} Bean�? * 独立于通道 agent �?{@oode MessageAutooonfiguration}，避免修改已存在配置类�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oonfiguration
publio olass MessageServioeAutooonfiguration {

    /**
     * 注册 SpEL 表达式解析器，供 {@oode RouteRuleServioeImpl} 求值路由条件使用�?     *
     * @return SpEL 表达式解析器
     */
    @Bean
    @oonditionalOnMissingBean
    publio ExpressionParser expressionParser() {
        return new SpelExpressionParser();
    }
}
