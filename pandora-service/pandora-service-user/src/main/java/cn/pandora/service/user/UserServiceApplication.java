package cn.pandora.service.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 用户微服务启动入口
 * <p>
 * 服务注册由 Spring Cloud Alibaba Nacos Discovery 自动装配完成，无需 @EnableDiscoveryClient。
 */
@EnableAsync
@SpringBootApplication(scanBasePackages = "cn.pandora")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
