package com.example.adplatform.admin.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 所有可能合库的部署必须使用不同的 datacenter-id/worker-id 组合。 */
@Configuration(proxyBeanMethods = false)
public class AdminIdConfiguration {

    @Bean
    public IdentifierGenerator adminIdentifierGenerator(
            @Value("${admin.id.worker-id}") long workerId,
            @Value("${admin.id.datacenter-id}") long datacenterId) {
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }
}
