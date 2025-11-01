package com.philosophy.util;

import com.philosophy.model.User;
import com.philosophy.model.UserLoginInfo;
import com.philosophy.repository.UserLoginInfoRepository;
import com.philosophy.repository.UserRepository;
import com.philosophy.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoCollectorTest {

    @Mock
    private UserService userService;

    @Mock
    private UserLoginInfoRepository userLoginInfoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserInfoCollector userInfoCollector;

    private User normalUser;
    private User adminUser;
    private User moderatorUser;

    @BeforeEach
    void setUp() {
        // 创建普通用户
        normalUser = new User();
        normalUser.setId(1L);
        normalUser.setUsername("normaluser");
        normalUser.setRole("USER");
        normalUser.setCreatedAt(LocalDateTime.now().minusHours(1));

        // 创建管理员用户
        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");
        adminUser.setRole("ADMIN");
        adminUser.setCreatedAt(LocalDateTime.now().minusHours(2));

        // 创建版主用户
        moderatorUser = new User();
        moderatorUser.setId(3L);
        moderatorUser.setUsername("moderator");
        moderatorUser.setRole("MODERATOR");
        moderatorUser.setCreatedAt(LocalDateTime.now().minusHours(3));
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_ShouldSkipAdminUser() {
        // 当新注册用户是管理员时，应该跳过重复检测
        when(userService.getUserById(2L)).thenReturn(adminUser);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(2L, request);

        assertTrue(result.isEmpty());
        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_ShouldSkipModeratorUser() {
        // 当新注册用户是版主时，应该跳过重复检测
        when(userService.getUserById(3L)).thenReturn(moderatorUser);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(3L, request);

        assertTrue(result.isEmpty());
        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_ShouldNotDeleteAdminUsers() {
        // 即使发现重复的管理员用户，也不应该删除
        when(userService.getUserById(1L)).thenReturn(normalUser);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getAttribute("__device_id__")).thenReturn("device123");

        // 模拟找到重复的管理员用户
        List<User> duplicateUsers = new ArrayList<>();
        duplicateUsers.add(adminUser);
        when(userLoginInfoRepository.findDuplicateUsersByDeviceIdSince(anyString(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(duplicateUsers);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(1L, request);

        assertTrue(result.isEmpty());
        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_ShouldNotDeleteModeratorUsers() {
        // 即使发现重复的版主用户，也不应该删除
        when(userService.getUserById(1L)).thenReturn(normalUser);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getAttribute("__device_id__")).thenReturn("device123");

        // 模拟找到重复的版主用户
        List<User> duplicateUsers = new ArrayList<>();
        duplicateUsers.add(moderatorUser);
        when(userLoginInfoRepository.findDuplicateUsersByDeviceIdSince(anyString(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(duplicateUsers);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(1L, request);

        assertTrue(result.isEmpty());
        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_ShouldDeleteOnlyOlderNormalUsers() {
        // 应该只删除注册时间更早的普通用户
        when(userService.getUserById(1L)).thenReturn(normalUser);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getAttribute("__device_id__")).thenReturn("device123");

        // 创建一个更早注册的普通用户
        User olderUser = new User();
        olderUser.setId(4L);
        olderUser.setUsername("olderuser");
        olderUser.setRole("USER");
        olderUser.setCreatedAt(LocalDateTime.now().minusHours(2));

        List<User> duplicateUsers = new ArrayList<>();
        duplicateUsers.add(olderUser);
        when(userLoginInfoRepository.findDuplicateUsersByDeviceIdSince(anyString(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(duplicateUsers);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(1L, request);

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0));
        verify(userService).deleteUser(4L);
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_ShouldNotDeleteNewerUsers() {
        // 不应该删除注册时间更新的用户
        when(userService.getUserById(1L)).thenReturn(normalUser);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getAttribute("__device_id__")).thenReturn("device123");

        // 创建一个更晚注册的普通用户
        User newerUser = new User();
        newerUser.setId(5L);
        newerUser.setUsername("neweruser");
        newerUser.setRole("USER");
        newerUser.setCreatedAt(LocalDateTime.now().plusHours(1));

        List<User> duplicateUsers = new ArrayList<>();
        duplicateUsers.add(newerUser);
        when(userLoginInfoRepository.findDuplicateUsersByDeviceIdSince(anyString(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(duplicateUsers);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(1L, request);

        assertTrue(result.isEmpty());
        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_BasedOnRegistrationRecords() {
        // 测试基于注册记录的重复检测
        when(userService.getUserById(1L)).thenReturn(normalUser);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getAttribute("__device_id__")).thenReturn("device123");

        // 创建一个更早注册的普通用户
        User olderUser = new User();
        olderUser.setId(4L);
        olderUser.setUsername("olderuser");
        olderUser.setRole("USER");
        olderUser.setCreatedAt(LocalDateTime.now().minusHours(2));

        // 模拟基于注册记录的查询
        List<User> recentUsers = new ArrayList<>();
        recentUsers.add(olderUser);
        when(userRepository.findDuplicateCandidates(any(LocalDateTime.class), anyLong()))
                .thenReturn(recentUsers);

        // 模拟登录信息匹配
        UserLoginInfo loginInfo = new UserLoginInfo();
        loginInfo.setDeviceId("device123");
        loginInfo.setIpAddress("192.168.1.1");
        loginInfo.setDeviceType("Desktop");
        
        List<UserLoginInfo> loginInfos = new ArrayList<>();
        loginInfos.add(loginInfo);
        when(userLoginInfoRepository.findByUserIdOrderByLoginTimeDesc(4L))
                .thenReturn(loginInfos);

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(1L, request);

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0));
        verify(userService).deleteUser(4L);
    }

    @Test
    void testCheckAndDeleteDuplicateAccounts_NoMatchingLoginInfo() {
        // 测试当没有匹配的登录信息时，不删除用户
        when(userService.getUserById(1L)).thenReturn(normalUser);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getAttribute("__device_id__")).thenReturn("device123");

        // 创建一个更早注册的普通用户
        User olderUser = new User();
        olderUser.setId(4L);
        olderUser.setUsername("olderuser");
        olderUser.setRole("USER");
        olderUser.setCreatedAt(LocalDateTime.now().minusHours(2));

        // 模拟基于注册记录的查询
        List<User> recentUsers = new ArrayList<>();
        recentUsers.add(olderUser);
        when(userRepository.findDuplicateCandidates(any(LocalDateTime.class), anyLong()))
                .thenReturn(recentUsers);

        // 模拟没有登录信息
        when(userLoginInfoRepository.findByUserIdOrderByLoginTimeDesc(4L))
                .thenReturn(new ArrayList<>());

        List<Long> result = userInfoCollector.checkAndDeleteDuplicateAccounts(1L, request);

        assertTrue(result.isEmpty());
        verify(userService, never()).deleteUser(anyLong());
    }
}
