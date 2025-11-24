package com.philosophy.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class DemoUtils {

	private DemoUtils() {
	}

	public static boolean isDemoAuthentication(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if ("ROLE_DEMO".equals(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isCurrentSessionDemo() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return isDemoAuthentication(auth);
	}
}










