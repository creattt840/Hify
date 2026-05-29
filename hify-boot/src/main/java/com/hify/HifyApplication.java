package com.hify;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class
})
@MapperScan(value = "com.hify.modules", annotationClass = org.apache.ibatis.annotations.Mapper.class,
        sqlSessionFactoryRef = "sqlSessionFactory")
public class HifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
    }
}
