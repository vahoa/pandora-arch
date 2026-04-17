package cn.pandora.gateway.handler;

import cn.pandora.common.result.Result;
import cn.pandora.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局异常处理器（反应式）。
 * <p>
 * 接管 Spring Cloud Gateway 的默认错误响应，把所有 5xx / 4xx 错误统一成 {@link Result} JSON 格式。
 * <ul>
 *   <li>NotFoundException（下游不可达 / 无路由匹配） -> 503</li>
 *   <li>其它未分类异常 -> 500</li>
 * </ul>
 */
@Configuration
public class GatewayExceptionHandler {

    @Bean
    @Order(-1)
    public GlobalErrorWebExceptionHandler globalErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ServerCodecConfigurer serverCodecConfigurer,
            ApplicationContext applicationContext,
            ObjectMapper objectMapper) {

        GlobalErrorWebExceptionHandler handler = new GlobalErrorWebExceptionHandler(
                errorAttributes, webProperties.getResources(), applicationContext, objectMapper);
        handler.setMessageReaders(serverCodecConfigurer.getReaders());
        handler.setMessageWriters(serverCodecConfigurer.getWriters());
        return handler;
    }

    @Slf4j
    public static class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

        private final ObjectMapper objectMapper;

        public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                              WebProperties.Resources resources,
                                              ApplicationContext applicationContext,
                                              ObjectMapper objectMapper) {
            super(errorAttributes, resources, applicationContext);
            this.objectMapper = objectMapper;
        }

        @Override
        protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
            return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
        }

        private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
            Throwable error = getError(request);
            Result<Void> body;
            HttpStatus status;

            if (error instanceof NotFoundException) {
                // SCG 抛出的下游不可达（lb:// 找不到实例等）
                status = HttpStatus.SERVICE_UNAVAILABLE;
                body = Result.failure(ResultCode.SERVICE_UNAVAILABLE,
                        "下游服务不可达或无匹配路由：" + error.getMessage());
            } else if (error instanceof ResponseStatusException rse) {
                // WebFlux 抛出的 404（例如：归一化后的路径找不到任何路由或静态资源）
                // 这种情况语义上是 404，而不是 500
                HttpStatus rseStatus = HttpStatus.resolve(rse.getStatusCode().value());
                if (rseStatus != null && rseStatus.is4xxClientError()) {
                    status = rseStatus;
                    ResultCode rc = (rseStatus == HttpStatus.NOT_FOUND)
                            ? ResultCode.NOT_FOUND
                            : ResultCode.INTERNAL_ERROR;
                    body = Result.failure(rc, friendlyMessage(rseStatus, rse.getReason()));
                } else {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                    body = Result.failure(ResultCode.INTERNAL_ERROR,
                            "网关内部异常：" + safe(rse.getReason(), error.getMessage()));
                }
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                body = Result.failure(ResultCode.INTERNAL_ERROR,
                        "网关内部异常：" + safe(null, error.getMessage()));
            }
            log.warn("[GW-ERR] path={} status={} err={}", request.path(), status.value(), error.toString());

            return ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(toBytes(body)));
        }

        private String friendlyMessage(HttpStatus status, String reason) {
            return switch (status) {
                case NOT_FOUND -> "资源不存在或路由未配置：" + safe(reason, "no route");
                case METHOD_NOT_ALLOWED -> "HTTP 方法不被允许：" + safe(reason, "method not allowed");
                case BAD_REQUEST -> "请求参数/格式错误：" + safe(reason, "bad request");
                default -> status.getReasonPhrase() + "：" + safe(reason, "");
            };
        }

        private String safe(String preferred, String fallback) {
            if (preferred != null && !preferred.isBlank()) return preferred;
            return fallback == null ? "" : fallback;
        }

        private byte[] toBytes(Result<Void> body) {
            try {
                return objectMapper.writeValueAsBytes(body);
            } catch (Exception e) {
                return ("{\"code\":" + body.getCode() + ",\"message\":\"" + body.getMessage() + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
            }
        }
    }
}
