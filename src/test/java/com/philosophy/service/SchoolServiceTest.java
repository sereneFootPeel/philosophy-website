package com.philosophy.service;

import com.philosophy.model.*;
import com.philosophy.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PhilosopherRepository philosopherRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserContentEditRepository userContentEditRepository;

    @Mock
    private TranslationService translationService;

    @Mock
    private UserFollowService userFollowService;

    @InjectMocks
    private SchoolService schoolService;

    private School parentSchool;
    private School childSchool;
    private Content content1;
    private Content content2;

    @BeforeEach
    void setUp() {
        // 创建父流派
        parentSchool = new School();
        parentSchool.setId(1L);
        parentSchool.setName("父流派");
        parentSchool.setContents(new ArrayList<>());

        // 创建子流派
        childSchool = new School();
        childSchool.setId(2L);
        childSchool.setName("子流派");
        childSchool.setParent(parentSchool);
        childSchool.setContents(new ArrayList<>());

        // 创建内容
        content1 = new Content();
        content1.setId(1L);
        content1.setContent("内容1");
        content1.setSchool(childSchool);

        content2 = new Content();
        content2.setId(2L);
        content2.setContent("内容2");
        content2.setSchool(childSchool);

        // 设置子流派的内容列表
        childSchool.getContents().add(content1);
        childSchool.getContents().add(content2);

        // 设置父流派的子流派列表
        parentSchool.getChildren().add(childSchool);
    }

    @Test
    void testDeleteSchoolWithParent_ShouldReassignContentsToParent() {
        // 模拟查找子流派
        when(schoolRepository.findById(2L)).thenReturn(Optional.of(childSchool));

        // 执行删除
        schoolService.deleteSchool(2L);

        // 验证内容被重新分配到父流派
        verify(contentRepository, times(2)).save(any(Content.class));
        
        // 验证内容1被重新分配
        assert content1.getSchool() == parentSchool;
        
        // 验证内容2被重新分配
        assert content2.getSchool() == parentSchool;

        // 验证子流派被删除
        verify(schoolRepository).delete(childSchool);
    }

    @Test
    void testDeleteSchoolWithoutParent_ShouldUnassignContents() {
        // 创建一个没有父流派的顶级流派
        School topLevelSchool = new School();
        topLevelSchool.setId(3L);
        topLevelSchool.setName("顶级流派");
        topLevelSchool.setParent(null);
        topLevelSchool.setContents(new ArrayList<>());

        Content content3 = new Content();
        content3.setId(3L);
        content3.setContent("内容3");
        content3.setSchool(topLevelSchool);

        topLevelSchool.getContents().add(content3);

        // 模拟查找顶级流派
        when(schoolRepository.findById(3L)).thenReturn(Optional.of(topLevelSchool));

        // 执行删除
        schoolService.deleteSchool(3L);

        // 验证内容被取消流派关联
        verify(contentRepository, times(1)).save(any(Content.class));
        
        // 验证内容3被取消流派关联
        assert content3.getSchool() == null;

        // 验证顶级流派被删除
        verify(schoolRepository).delete(topLevelSchool);
    }

    @Test
    void testDeleteSchoolWithNoContents_ShouldNotCallContentRepository() {
        // 创建一个没有内容的流派
        School emptySchool = new School();
        emptySchool.setId(4L);
        emptySchool.setName("空流派");
        emptySchool.setParent(parentSchool);
        emptySchool.setContents(new ArrayList<>());

        // 模拟查找空流派
        when(schoolRepository.findById(4L)).thenReturn(Optional.of(emptySchool));

        // 执行删除
        schoolService.deleteSchool(4L);

        // 验证没有调用内容保存方法
        verify(contentRepository, never()).save(any(Content.class));

        // 验证空流派被删除
        verify(schoolRepository).delete(emptySchool);
    }
}
