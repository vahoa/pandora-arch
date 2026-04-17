package cn.pandora.gateway.filter;

import cn.hutool.core.util.IdUtil;
import cn.pandora.gateway.constant.GatewayConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 全链路请求日志过滤器。
 * <p>
 * 负责：
 * <ul>
 *   <li>为每个请求生成 {@code X-Pandora-Trace-Id} / {@code X-Pandora-Request-Id}，并透传下游；</li>
 *   <li>注入 {@code X-Pandora-Client-Ip}（兼容 X-Forwarded-For 代理链）；</li>
 *   <li>记录接入 / 完成日志及耗时。</li>
 * </ul>
 * 优先级最高（-200），保证后续过滤器都能读到 TraceId。
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    private static final String START_TIME_ATTR = "PANDORA_GW_START_TIME";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String traceId = firstNonBlank(
                request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID),
                IdUtil.fastSimpleUUID());
        String requestId = IdUtil.fastSimpleUUID();
        String clientIp  = resolveClientIp(request);

        ServerHttpRequest mutated = request.mutate()
                .header(GatewayConstants.HEADER_TRACE_ID,   traceId)
                .header(GatewayConstants.HEADER_REQUEST_ID, requestId)
                .header(GatewayConstants.HEADER_CLIENT_IP,  clientIp)
                .build();

        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());
        exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        log.info("[GW-IN]  traceId={} method={} path={} ip={}",
                traceId, request.getMethod(), request.getURI().getPath(), clientIp);

        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signalType -> {
                    Long start = exchange.getAttribute(START_TIME_ATTR);
                    long cost = start == null ? 0 : System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 0 : exchange.getResponse().getStatusCode().value();
                    log.info("[GW-OUT] traceId={} status={} cost={}ms", traceId, status, cost);
                });
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    /** 最高优先级：先生成 TraceId，再做鉴权、限流 */
    @Override
    public int getOrder() {
        return -200;
    }
}
