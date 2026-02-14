package com.philosophy.service;

import com.philosophy.model.TestResult;
import com.philosophy.model.User;
import com.philosophy.repository.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TestResultService {

    private final TestResultRepository testResultRepository;

    public TestResultService(TestResultRepository testResultRepository) {
        this.testResultRepository = testResultRepository;
    }

    @Transactional
    public TestResult save(User user, String testType, String resultSummary, String resultJson, boolean isPublic) {
        TestResult r = new TestResult();
        r.setUser(user);
        r.setTestType(testType);
        r.setResultSummary(resultSummary != null ? resultSummary : "");
        r.setResultJson(resultJson);
        r.setPublic(isPublic);
        return testResultRepository.save(r);
    }

    /** 当前用户查看自己的所有记录 */
    @Transactional(readOnly = true)
    public List<TestResult> findByUserId(Long userId) {
        return testResultRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 在用户主页展示：本人看全部，访客只看公开 */
    @Transactional(readOnly = true)
    public List<TestResult> findVisibleForProfile(Long profileUserId, User viewer) {
        if (viewer != null && viewer.getId().equals(profileUserId)) {
            return testResultRepository.findByUserIdOrderByCreatedAtDesc(profileUserId);
        }
        return testResultRepository.findByUserIdAndIsPublicTrueOrderByCreatedAtDesc(profileUserId);
    }

    @Transactional(readOnly = true)
    public Optional<TestResult> findById(Long id) {
        return testResultRepository.findById(id);
    }

    /** 查看单条记录：仅当本人或公开时可见 */
    @Transactional(readOnly = true)
    public Optional<TestResult> findByIdForView(Long id, User viewer) {
        return testResultRepository.findById(id).filter(r -> {
            if (viewer == null) return r.isPublic();
            if (r.getUser().getId().equals(viewer.getId())) return true;
            return r.isPublic();
        });
    }

    @Transactional
    public boolean updateVisibility(Long id, Long userId, boolean isPublic) {
        Optional<TestResult> opt = testResultRepository.findById(id);
        if (opt.isEmpty() || !opt.get().getUser().getId().equals(userId)) return false;
        TestResult r = opt.get();
        r.setPublic(isPublic);
        testResultRepository.save(r);
        return true;
    }

    @Transactional
    public boolean deleteByIdAndUser(Long id, Long userId) {
        Optional<TestResult> opt = testResultRepository.findById(id);
        if (opt.isEmpty() || !opt.get().getUser().getId().equals(userId)) return false;
        testResultRepository.delete(opt.get());
        return true;
    }
}
