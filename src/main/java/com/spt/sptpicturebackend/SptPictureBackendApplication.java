package com.spt.sptpicturebackend;

import cn.dev33.satoken.strategy.SaJsonStrategy;
import com.spt.sptpicturebackend.model.entity.User;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author zhiyuan
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.spt.sptpicturebackend.mapper")
public class SptPictureBackendApplication {

    public static void main(String[] args) {
        // Sa-Token 1.46 protects Jackson polymorphic deserialization with an allow-list.
        // User is stored in the Sa-Token session and must be registered before startup.
        SaJsonStrategy.instance.registerAllowType(User.class);
        SpringApplication.run(SptPictureBackendApplication.class, args);
    }

}
