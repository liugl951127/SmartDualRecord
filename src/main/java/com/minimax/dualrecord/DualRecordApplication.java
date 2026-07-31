package com.minimax.dualrecord;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 双录一体化中台 · 主启动类
 *
 * 一个中台两条跑道：把线下面对面双录、远程视频双录、自助 AI 数字人双录
 * 整合到统一的流程编排、质检规则、存证链路中。
 *
 * @author Mavis
 * @since 2026
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.minimax.dualrecord.repository")
public class DualRecordApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DualRecordApplication.class);
        app.setLogStartupInfo(true);
        app.run(args);
    }
}
