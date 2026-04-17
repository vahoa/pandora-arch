package cn.pandora.infrastructure.config;

import cn.pandora.application.ai.AiService;
import cn.pandora.infrastructure.ai.AiServiceFallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAutoConfiguration {

    @Configuration
    @ConditionalOnClass(name = "org.springframework.ai.chat.client.ChatClient")
    static class SpringAiConfiguration {

        @Bean
        @ConditionalOnClass(name = "org.springframework.ai.chat.client.ChatClient")
        public AiService aiChatService(org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder) {
            return new cn.pandora.infrastructure.ai.AiChatService(chatClientBuilder);
        }
    }

    @Bean
    @ConditionalOnMissingBean(AiService.class)
    public AiService aiServiceFallback() {
        return new AiServiceFallback();
    }
}
