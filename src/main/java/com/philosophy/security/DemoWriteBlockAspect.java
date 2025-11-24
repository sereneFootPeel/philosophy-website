package com.philosophy.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 在演示（DEMO）会话中，屏蔽所有 Repository 的写操作（save/delete/flush 等）。
 * - save: 直接返回入参（或第一参数/返回原对象），不落库
 * - delete/flush: 直接吞掉
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoWriteBlockAspect {

	/**
	 * 拦截所有 Spring Data Repository 的 save 方法
	 */
	@Around("execution(* org.springframework.data.repository.CrudRepository.save(..)) || " +
		"execution(* org.springframework.data.repository.CrudRepository.saveAll(..))")
	public Object aroundSave(ProceedingJoinPoint pjp) throws Throwable {
		if (DemoUtils.isCurrentSessionDemo()) {
			Object[] args = pjp.getArgs();
			// save(entity) -> 直接返回入参
			// saveAll(entities) -> 直接返回入参
			return (args != null && args.length > 0) ? args[0] : null;
		}
		return pjp.proceed();
	}

	/**
	 * 拦截 delete / deleteAll
	 */
	@Around("execution(* org.springframework.data.repository.CrudRepository.delete*(..))")
	public Object aroundDelete(ProceedingJoinPoint pjp) throws Throwable {
		if (DemoUtils.isCurrentSessionDemo()) {
			// 吞掉删除
			return null;
		}
		return pjp.proceed();
	}

	/**
	 * 拦截 flush
	 */
	@Around("execution(* org.springframework.data.jpa.repository.JpaRepository.flush(..))")
	public Object aroundFlush(ProceedingJoinPoint pjp) throws Throwable {
		if (DemoUtils.isCurrentSessionDemo()) {
			return null;
		}
		return pjp.proceed();
	}
}










