package com.chainflow.purchase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.chainflow.purchase.mapper")
@SpringBootApplication(scanBasePackages = "com.chainflow")
public class PurchaseApplication {
    public static void main(String[] args) { SpringApplication.run(PurchaseApplication.class, args); }
}
