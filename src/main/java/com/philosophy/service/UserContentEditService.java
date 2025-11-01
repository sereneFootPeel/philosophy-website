package com.philosophy.service;

import com.philosophy.model.*;
import com.philosophy.repository.UserContentEditRepository;
import com.philosophy.repository.PhilosopherRepository;
import com.philosophy.repository.SchoolRepository;
import com.philosophy.repository.ContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserContentEditService {
    
    @Autowired
    private UserContentEditRepository userContentEditRepository;
    
    @Autowired
    private PhilosopherRepository philosopherRepository;
    
    @Autowired
    private SchoolRepository schoolRepository;
    
    @Autowired
    private ContentRepository contentRepository;
    
    // 创建用户编辑
    public UserContentEdit createEdit(User user, Long originalContentId, Long philosopherId, Long schoolId, String title, String content, String contentEn) {
        Content originalContent = null;
        if (originalContentId != null) {
            originalContent = contentRepository.findById(originalContentId)
                .orElseThrow(() -> new RuntimeException("原始内容不存在"));
        }

        Philosopher philosopher = philosopherRepository.findById(philosopherId)
            .orElseThrow(() -> new RuntimeException("哲学家不存在"));

        School school = schoolRepository.findById(schoolId)
            .orElseThrow(() -> new RuntimeException("流派不存在"));

        UserContentEdit edit = new UserContentEdit(user, originalContent, philosopher, school, title, content, contentEn);
        return userContentEditRepository.save(edit);
    }
    
    // 更新用户编辑
    public UserContentEdit updateEdit(Long editId, Long philosopherId, Long schoolId, String title, String content, String contentEn) {
        UserContentEdit edit = userContentEditRepository.findById(editId)
            .orElseThrow(() -> new RuntimeException("编辑不存在"));


        Philosopher philosopher = philosopherRepository.findById(philosopherId)
            .orElseThrow(() -> new RuntimeException("哲学家不存在"));

        School school = schoolRepository.findById(schoolId)
            .orElseThrow(() -> new RuntimeException("流派不存在"));

        edit.setPhilosopher(philosopher);
        edit.setSchool(school);
        edit.setTitle(title);
        edit.setContent(content);
        edit.setContentEn(contentEn);

        return userContentEditRepository.save(edit);
    }
    
    // 删除用户编辑
    public void deleteEdit(Long editId) {
        UserContentEdit edit = userContentEditRepository.findById(editId)
            .orElseThrow(() -> new RuntimeException("编辑不存在"));
        
        
        userContentEditRepository.delete(edit);
    }
    
    // 获取用户的所有编辑
    public Page<UserContentEdit> getUserEdits(Long userId, Pageable pageable) {
        return userContentEditRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
    }
    
    // 获取特定内容的所有用户编辑
    public List<UserContentEdit> getContentEdits(Long contentId) {
        return userContentEditRepository.findByOriginalContentIdOrderByUpdatedAtDesc(contentId);
    }
    
    
    
    // 获取编辑详情
    public Optional<UserContentEdit> getEditById(Long editId) {
        return userContentEditRepository.findById(editId);
    }
    
    // 获取用户编辑统计
    public long getUserEditCount(Long userId) {
        return userContentEditRepository.countByUserId(userId);
    }
    
    // 获取待审核编辑数量
    public long getPendingEditCount() {
        return userContentEditRepository.countByStatus(UserContentEdit.EditStatus.PENDING);
    }
    
    // 获取内容的最新用户编辑
    public List<UserContentEdit> getLatestContentEdits(Long contentId, Pageable pageable) {
        return userContentEditRepository.findLatestByOriginalContentId(contentId, pageable);
    }
}
