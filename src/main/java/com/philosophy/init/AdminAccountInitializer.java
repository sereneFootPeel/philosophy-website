package com.philosophy.init;

import com.philosophy.model.User;
import com.philosophy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 在应用启动时自动创建或更新管理员账户
        logger.info("初始化管理员账户...");
        createOrUpdateAdminAccount();
        logger.info("管理员账户初始化完成");
    }

    private void createOrUpdateAdminAccount() {
        User admin = userRepository.findByUsername("admin").orElse(null);

        if (admin == null) {
            // 创建新管理员账户
            admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@philosophy.com");
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            admin.setAccountLocked(false);
            admin.setFailedLoginAttempts(0);
            admin.setAdminLoginAttempts(0);
            admin.setProfilePrivate(false);
            admin.setLikeCount(0);
        }

        // 更新密码为000000
        admin.setPassword(passwordEncoder.encode("000000"));
        userRepository.save(admin);
        logger.info("管理员密码已更新为: 000000");
    }
}