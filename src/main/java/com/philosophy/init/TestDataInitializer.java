package com.philosophy.init;

import com.philosophy.model.Comment;
import com.philosophy.model.Content;
import com.philosophy.model.User;
import com.philosophy.repository.CommentRepository;
import com.philosophy.repository.ContentRepository;
import com.philosophy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TestDataInitializer.class);

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // 暂时禁用测试数据初始化，避免乐观锁冲突
        logger.info("Test data initialization is disabled to avoid optimistic locking conflicts");
        return;
        
        // 以下代码暂时注释掉
        /*
        // 检查是否已经有评论数据
        long commentCount = commentRepository.count();
        if (commentCount > 0) {
            logger.info("Comments already exist, skipping test data initialization");
            return;
        }

        logger.info("Initializing test comment data...");

        // 获取第一个用户和内容
        List<User> users = userRepository.findAll();
        List<Content> contents = contentRepository.findAll();

        if (users.isEmpty() || contents.isEmpty()) {
            logger.warn("No users or contents found, cannot create test comments");
            return;
        }

        User testUser = users.get(0);
        Content testContent = contents.get(0);
        
        // 确保Content实体的version字段不为null
        if (testContent.getVersion() == null) {
            testContent.setVersion(0L);
            contentRepository.save(testContent);
        }

        // 创建测试评论
        Comment publicComment = new Comment(testContent, testUser, "这是一个公开的测试评论，未登录用户应该能看到");
        publicComment.setPrivate(false); // 明确设置为公开
        publicComment.setStatus(0); // 正常状态
        commentRepository.save(publicComment);

        Comment privateComment = new Comment(testContent, testUser, "这是一个私密评论，只有作者和管理员能看到");
        privateComment.setPrivate(true); // 设置为私密
        privateComment.setStatus(0); // 正常状态
        commentRepository.save(privateComment);

        Comment hiddenComment = new Comment(testContent, testUser, "这是一个被管理员隐藏的评论");
        hiddenComment.setPrivate(false); // 公开
        hiddenComment.setStatus(1); // 管理员隐藏
        commentRepository.save(hiddenComment);

        logger.info("Test comment data initialized successfully");
        logger.info("Created {} test comments", commentRepository.count());
        */
    }
}
