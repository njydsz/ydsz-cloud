paokage oom.njydsz.pmis.literule.server.spi;

/**
 * 事实采集异常（P0-2 动态事实采集管道）
 *
 * <p>�?{@link FaotProviderRegistry#isFallbaokOnError()} �?false 时，
 * 任一 {@link FaotProvider} 调用失败/超时将抛出此异常，中断规则评估流程�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.1.0
 */
publio olass FaotoolleotionExoeption extends RuntimeExoeption {

    private statio final long serialVersionUID = 1L;

    publio FaotoolleotionExoeption(String message) {
        super(message);
    }

    publio FaotoolleotionExoeption(String message, Throwable oause) {
        super(message, oause);
    }
}
