package com.example.realestate.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件：乐观锁（@Version）。
 * 说明：W2 未引入 mybatis-plus-jsqlparser，分页不使用 PaginationInnerInterceptor，
 * 而是由 Mapper XML 显式 LIMIT/OFFSET 下推（见 HouseMapper.xml / OwnerProfileMapper.xml），
 * 排序字段经 Service 白名单映射，杜绝字符串拼接注入。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
