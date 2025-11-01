package com.philosophy.migration;

import com.philosophy.service.PhilosopherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SchoolRecalculationMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchoolRecalculationMigration.class);

    @Autowired
    private PhilosopherService philosopherService;

    @Override
    public void run(String... args) throws Exception {
        logger.info("开始重新计算所有哲学家的流派关联...");
        
        // 重新计算所有哲学家的流派
        philosopherService.recalculateAllPhilosopherSchools();
        
        logger.info("哲学家流派关联重新计算完成！");
    }
}