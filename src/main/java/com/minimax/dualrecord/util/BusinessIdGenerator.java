package com.minimax.dualrecord.util;

import com.minimax.dualrecord.domain.enums.BusinessType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务 ID 生成器 · Snowflake 简化版
 *
 * 格式: {前缀}{YYYYMMDD}-{6位序号}
 * 示例: ORD20260801-000042
 *
 * 业务 ID 是跨段串联、事后复盘、合规报送的唯一锚点
 */
@Component
public class BusinessIdGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${dual-record.business-id.insurance-prefix:ORD}")
    private String insurancePrefix;

    @Value("${dual-record.business-id.wealth-prefix:BNK}")
    private String wealthPrefix;

    @Value("${dual-record.business-id.fund-prefix:FND}")
    private String fundPrefix;

    private final AtomicLong insuranceCounter = new AtomicLong(0);
    private final AtomicLong wealthCounter = new AtomicLong(0);
    private final AtomicLong fundCounter = new AtomicLong(0);

    public String generate(BusinessType type) {
        String prefix = switch (type) {
            case INSURANCE -> insurancePrefix;
            case WEALTH -> wealthPrefix;
            case FUND -> fundPrefix;
        };
        AtomicLong counter = switch (type) {
            case INSURANCE -> insuranceCounter;
            case WEALTH -> wealthCounter;
            case FUND -> fundCounter;
        };
        long seq = counter.incrementAndGet();
        if (seq > 999_999) {
            counter.set(0);
            seq = counter.incrementAndGet();
        }
        return String.format("%s%s-%06d", prefix, LocalDate.now().format(DATE_FORMAT), seq);
    }

    public String generateRecId() {
        return String.format("REC%s-%06d",
                LocalDate.now().format(DATE_FORMAT),
                System.nanoTime() % 1_000_000);
    }

    public String generateQaId() {
        return String.format("QA%s-%06d",
                LocalDate.now().format(DATE_FORMAT),
                System.nanoTime() % 1_000_000);
    }
}
