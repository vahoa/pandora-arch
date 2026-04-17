package cn.pandora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * DDD架构底座 —— 主应用启动入口（资源服务器）
 */
@EnableAsync
@SpringBootApplication
public class PandoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(PandoraApplication.class, args);
    }
}
