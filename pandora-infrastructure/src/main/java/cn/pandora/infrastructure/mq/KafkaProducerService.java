package cn.pandora.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka 生产者 —— 统一封装，支持异步/同步发送。
 *
 * <p>按需装配：仅在以下条件均满足时才会加载，避免未接入 Kafka 的服务因强制注入
 * {@link KafkaTemplate} 而启动失败：</p>
 * <ul>
 *   <li>classpath 存在 {@link KafkaTemplate}（spring-kafka 已引入）</li>
 *   <li>配置开关 {@code pandora.kafka.enabled=true}（未开启的服务自动跳过本 Bean）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "pandora.kafka", name = "enabled", havingValue = "true")
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** 异步发送 —— 推荐，不阻塞调用方 */
    public CompletableFuture<SendResult<String, Object>> sendAsync(String topic, String key, Object payload) {
        return kafkaTemplate.send(topic, key, payload)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 发送失败 topic={} key={}", topic, key, ex);
                    } else if (log.isDebugEnabled()) {
                        log.debug("Kafka 发送成功 topic={} partition={} offset={}",
                                topic,
                                res.getRecordMetadata().partition(),
                                res.getRecordMetadata().offset());
                    }
                });
    }

    /** 同步发送 —— 阻塞直到有结果（用于事务一致性要求高的场景） */
    public SendResult<String, Object> sendSync(String topic, String key, Object payload) {
        try {
            return sendAsync(topic, key, payload).get();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 同步发送失败: " + topic, e);
        }
    }

    public CompletableFuture<SendResult<String, Object>> send(String topic, Object payload) {
        return sendAsync(topic, null, payload);
    }
}
