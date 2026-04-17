package cn.pandora.auth.service;

import cn.pandora.auth.config.AuthProperties;
import cn.pandora.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务 —— 基于 Redis 存储，支持模拟模式
 */
@Slf4j
@Service
public class SmsService {

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String SMS_LIMIT_PREFIX = "sms:limit:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public SmsService(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
    }

    /**
     * 发送验证码
     */
    public void sendCode(String phone) {
        Long count = redisTemplate.opsForValue().increment(SMS_LIMIT_PREFIX + phone);
        if (count != null && count == 1) {
            redisTemplate.expire(SMS_LIMIT_PREFIX + phone, 1, TimeUnit.DAYS);
        }
        if (count != null && count > authProperties.getSms().getDailyLimit()) {
            throw new BusinessException("今日验证码发送次数已达上限");
        }

        String code;
        if (Boolean.TRUE.equals(authProperties.getSms().getMockEnabled())) {
            code = "888888";
            log.info("[模拟模式] 手机号 {} 的验证码: {}", phone, code);
        } else {
            code = generateCode(authProperties.getSms().getCodeLength());
            doSendSms(phone, code);
        }

        redisTemplate.opsForValue().set(
                SMS_CODE_PREFIX + phone, code,
                authProperties.getSms().getExpireMinutes(), TimeUnit.MINUTES);
    }

    /**
     * 验证验证码
     */
    public boolean verifyCode(String phone, String code) {
        String key = SMS_CODE_PREFIX + phone;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /**
     * 实际发送短信 —— 对接阿里云/腾讯云等短信平台
     * 生产环境需替换此实现
     */
    protected void doSendSms(String phone, String code) {
        log.warn("短信发送未实现，请对接实际短信平台。phone={}, code={}", phone, code);
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
