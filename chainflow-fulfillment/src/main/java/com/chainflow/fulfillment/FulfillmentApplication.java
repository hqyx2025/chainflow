package com.chainflow.fulfillment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.chainflow.fulfillment.mapper")
@SpringBootApplication(scanBasePackages = "com.chainflow")
public class FulfillmentApplication {
    public static void main(String[] args) { SpringApplication.run(FulfillmentApplication.class, args); }
}
