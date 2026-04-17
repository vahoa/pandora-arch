package cn.pandora.cloud.config;

import cn.pandora.common.result.Result;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sentinel 限流 / 降级全局异常兜底
 * <p>
 * 不依赖 adapter-spring-webmvc 的 BlockExceptionHandler（其包名在 Spring MVC 6/7 下发生变化），
 * 通过 @RestControllerAdvice 直接捕获 Sentinel 抛出的 BlockException 及其子类。
 */
@Slf4j
@Configuration
@ConditionalOnClass(BlockException.class)
@RestControllerAdvice
public class SentinelConfig {

    @ExceptionHandler(FlowException.class)
    public ResponseEntity<Result<Void>> handleFlow(FlowException e) {
        log.warn("Sentinel 流控: rule={}", e.getRule());
        return ResponseEntity.status(429).body(Result.failure(429, "请求过于频繁，请稍后重试"));
    }

    @ExceptionHandler(DegradeException.class)
    public ResponseEntity<Result<Void>> handleDegrade(DegradeException e) {
        log.warn("Sentinel 熔断: rule={}", e.getRule());
        return ResponseEntity.status(503).body(Result.failure(503, "服务暂时不可用，请稍后重试"));
    }

    @ExceptionHandler(BlockException.class)
    public ResponseEntity<Result<Void>> handleBlock(BlockException e) {
        log.warn("Sentinel 拦截: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(429).body(Result.failure(429, "请求被限流/熔断"));
    }
}
