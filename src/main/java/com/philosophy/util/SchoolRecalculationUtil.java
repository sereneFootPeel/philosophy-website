package com.philosophy.util;

import com.philosophy.service.PhilosopherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SchoolRecalculationUtil {

    @Autowired
    private PhilosopherService philosopherService;

    /**
     * 重新计算所有哲学家的流派关联
     * 可以在需要时调用此方法，例如数据迁移后
     */
    public void recalculateAllPhilosopherSchools() {
        philosopherService.recalculateAllPhilosopherSchools();
    }
}