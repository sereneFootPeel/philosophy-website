package com.philosophy.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 用于“admin 假登录”的演示用户，具备 ADMIN 权限以及 DEMO 会话标记。
 */
public class DemoUser implements UserDetails {

	private final String username;

	public DemoUser(String username) {
		this.username = username;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		List<GrantedAuthority> authorities = new ArrayList<>();
		// 赋予管理员权限
		authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
		// 赋予演示模式标记权限
		authorities.add(new SimpleGrantedAuthority("ROLE_DEMO"));
		return authorities;
	}

	@Override
	public String getPassword() {
		// 演示用户无需真实密码
		return "";
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}










