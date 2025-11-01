package com.philosophy.service;

import com.philosophy.model.Comment;
import com.philosophy.model.Content;
import com.philosophy.model.User;
import com.philosophy.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

@Service
public class CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final ContentService contentService;
    private final UserBlockService userBlockService;
    private final ModeratorBlockService moderatorBlockService;

    public CommentService(CommentRepository commentRepository, ContentService contentService, 
                         UserBlockService userBlockService, ModeratorBlockService moderatorBlockService) {
        this.commentRepository = commentRepository;
        this.contentService = contentService;
        this.userBlockService = userBlockService;
        this.moderatorBlockService = moderatorBlockService;
    }

    @Transactional(readOnly = true)
    public List<Comment> findByContentId(Long contentId) {
        // 获取顶级评论（无父评论的评论）
        return commentRepository.findByContentIdAndParentIsNullOrderByCreatedAtDesc(contentId);
    }
    
    @Transactional(readOnly = true)
    public List<Comment> findRepliesByParentId(Long parentId) {
        return commentRepository.findRepliesByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public long countByContentId(Long contentId) {
        return commentRepository.countByContentId(contentId);
    }

    @Transactional(readOnly = true)
    public List<Comment> findByUserId(Long userId) {
        // 使用带有JOIN FETCH的查询方法来预加载关联的内容对象
        return commentRepository.findByUserIdWithContent(userId);
    }

    @Transactional(readOnly = true)
    public long countByUserId(Long userId) {
        return commentRepository.countByUserId(userId);
    }

    @Transactional
    public Comment saveComment(Long contentId, User user, String body) {
        Content content = contentService.getContentById(contentId);
        if (content == null) {
            throw new IllegalArgumentException("Content not found with id: " + contentId);
        }

        Comment comment = new Comment(content, user, body);
        // Ensure createdAt is set explicitly as a fallback
        if (comment.getCreatedAt() == null) {
            comment.setCreatedAt(java.time.LocalDateTime.now());
        }
        return commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long id) {
        commentRepository.deleteById(id);
    }

    @Transactional
    public void softDeleteComment(Long id, User deletedBy) {
        Comment comment = commentRepository.findById(id).orElse(null);
        if (comment != null) {
            comment.setDeletedAt(java.time.LocalDateTime.now());
            comment.setDeletedBy(deletedBy);
            commentRepository.save(comment);
        }
    }

    @Transactional
    public void blockComment(Long id, User blockedBy) {
        Comment comment = commentRepository.findById(id).orElse(null);
        if (comment != null) {
            comment.setBlocked(true);
            comment.setBlockedBy(blockedBy);
            comment.setBlockedAt(java.time.LocalDateTime.now());
            commentRepository.save(comment);
        }
    }

    @Transactional
    public void unblockComment(Long id) {
        Comment comment = commentRepository.findById(id).orElse(null);
        if (comment != null) {
            comment.setBlocked(false);
            comment.setBlockedBy(null);
            comment.setBlockedAt(null);
            commentRepository.save(comment);
        }
    }

    @Transactional
    public Comment saveReply(Long parentId, User user, String body) {
        Comment parent = commentRepository.findById(parentId).orElse(null);
        if (parent == null) {
            throw new IllegalArgumentException("Parent comment not found with id: " + parentId);
        }
        Comment reply = new Comment(parent.getContent(), user, body);
        reply.setParent(parent);
        // Ensure createdAt is set explicitly as a fallback
        if (reply.getCreatedAt() == null) {
            reply.setCreatedAt(java.time.LocalDateTime.now());
        }
        return commentRepository.save(reply);
    }

    @Transactional(readOnly = true)
    public Comment getCommentById(Long id) {
        return commentRepository.findById(id).orElse(null);
    }

    /**
     * 根据内容ID查找评论，支持隐私过滤
     * @param contentId 内容ID
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的评论列表
     */
    @Transactional(readOnly = true)
    public List<Comment> findByContentIdWithPrivacyFilter(Long contentId, User currentUser) {
        List<Comment> allComments = commentRepository.findByContentIdAndParentIsNullOrderByCreatedAtDesc(contentId);
        
        // 检查是否是管理员
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        
        List<Comment> commentsToFilter;
        if (isAdmin) {
            // 管理员可以看到所有评论（包括已删除的）
            commentsToFilter = allComments;
        } else {
            // 普通用户只能看到未删除的评论
            commentsToFilter = allComments.stream()
                    .filter(comment -> comment.getDeletedAt() == null)
                    .collect(java.util.stream.Collectors.toList());
        }
        
        return filterCommentsByPrivacy(commentsToFilter, currentUser);
    }

    /**
     * 根据用户ID查找评论，支持隐私过滤
     * @param userId 用户ID
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的评论列表
     */
    @Transactional(readOnly = true)
    public List<Comment> findByUserIdWithPrivacyFilter(Long userId, User currentUser) {
        List<Comment> allComments = commentRepository.findByUserIdWithContent(userId);
        // 过滤掉软删除的评论
        List<Comment> activeComments = allComments.stream()
                .filter(comment -> comment.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toList());
        return filterCommentsByPrivacy(activeComments, currentUser);
    }

    /**
     * 根据父评论ID查找回复，支持隐私过滤
     * @param parentId 父评论ID
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的回复列表
     */
    @Transactional(readOnly = true)
    public List<Comment> findRepliesByParentIdWithPrivacyFilter(Long parentId, User currentUser) {
        List<Comment> allReplies = commentRepository.findRepliesByParentId(parentId);
        // 过滤掉软删除的回复
        List<Comment> activeReplies = allReplies.stream()
                .filter(comment -> comment.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toList());
        return filterCommentsByPrivacy(activeReplies, currentUser);
    }

    /**
     * 根据流派ID列表查找评论，支持隐私过滤
     * @param schoolIds 流派ID列表
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的评论列表
     */
    @Transactional(readOnly = true)
    public List<Comment> findBySchoolIdsWithPrivacyFilter(List<Long> schoolIds, User currentUser) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 获取这些流派下的所有评论
        List<Comment> allComments = commentRepository.findBySchoolIdsOrderByCreatedAtDesc(schoolIds);
        
        // 检查是否是管理员
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        
        List<Comment> commentsToFilter;
        if (isAdmin) {
            // 管理员可以看到所有评论（包括已删除的）
            commentsToFilter = allComments;
        } else {
            // 普通用户只能看到未删除的评论
            commentsToFilter = allComments.stream()
                    .filter(comment -> comment.getDeletedAt() == null)
                    .collect(java.util.stream.Collectors.toList());
        }
        
        return filterCommentsByPrivacy(commentsToFilter, currentUser);
    }


    /**
     * 根据隐私设置和屏蔽关系过滤评论
     * @param comments 评论列表
     * @param currentUser 当前用户
     * @return 过滤后的评论列表
     */
    private List<Comment> filterCommentsByPrivacy(List<Comment> comments, User currentUser) {
        if (comments == null || comments.isEmpty()) {
            return comments;
        }

        List<Comment> filteredComments = new ArrayList<>();
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());

        // 添加调试日志
        logger.debug("Filtering {} comments for user: {}", comments.size(), 
            (currentUser != null ? currentUser.getUsername() : "anonymous"));

        for (Comment comment : comments) {
            boolean canView = false;
            
            // 管理员可以看到所有评论，不受任何屏蔽关系限制
            if (isAdmin) {
                canView = true;
                logger.debug("Comment {} visible to admin", comment.getId());
                filteredComments.add(comment);
                continue;
            }
            
            // 首先检查普通用户屏蔽关系：如果当前用户屏蔽了评论作者，则跳过该评论
            if (currentUser != null) {
                boolean isBlocked = userBlockService.isBlocked(currentUser.getId(), comment.getUser().getId());
                if (isBlocked) {
                    logger.debug("Comment {} is from blocked user, skipping", comment.getId());
                    continue; // 跳过被屏蔽用户的评论
                }
            }
            
            // 检查评论是否被屏蔽
            if (comment.isBlocked()) {
                // 被屏蔽的评论，只有评论作者本人和管理员可见
                if (currentUser != null && 
                    (comment.getUser().getId().equals(currentUser.getId()) || isAdmin)) {
                    canView = true;
                }
                logger.debug("Comment {} is blocked, canView: {}", comment.getId(), canView);
                if (canView) {
                    filteredComments.add(comment);
                }
                continue; // 跳过后续检查
            }
            
            // 检查版主屏蔽关系：如果评论作者在相关流派中被版主屏蔽
            if (currentUser != null) {
                // 获取评论所属内容的流派ID
                Long schoolId = comment.getContent().getSchool() != null ? comment.getContent().getSchool().getId() : null;
                if (schoolId != null) {
                    // 检查用户是否在该流派及其子流派中被版主屏蔽
                    boolean isModeratorBlocked = moderatorBlockService.isUserBlockedInSchoolAndSubSchools(comment.getUser().getId(), schoolId);
                    if (isModeratorBlocked) {
                        // 被版主屏蔽的用户评论，只有评论作者本人和管理员可见
                        if (comment.getUser().getId().equals(currentUser.getId()) || isAdmin) {
                            canView = true;
                        }
                        logger.debug("Comment {} is from moderator-blocked user, canView: {}", comment.getId(), canView);
                        if (canView) {
                            filteredComments.add(comment);
                        }
                        continue; // 跳过后续检查
                    }
                }
            }
            
            // 添加调试日志
            logger.debug("Comment {} - isPrivate: {}, status: {}", comment.getId(), 
                comment.isPrivate(), comment.getStatus());
            
            // 检查管理员设置的状态字段
            if (comment.getStatus() == 1) {
                // 状态为1（管理员设置隐藏），只有管理员和评论作者可见
                if (currentUser != null && 
                    (comment.getUser().getId().equals(currentUser.getId()) || isAdmin)) {
                    canView = true;
                }
                logger.debug("Comment {} is admin hidden, canView: {}", comment.getId(), canView);
            } else {
                // 状态为0（正常），继续检查用户隐私设置
                if (comment.isPrivate()) {
                    // 评论被设置为私密，只有评论作者和管理员可见
                    if (currentUser != null && 
                        (comment.getUser().getId().equals(currentUser.getId()) || isAdmin)) {
                        canView = true;
                    }
                    logger.debug("Comment {} is private, canView: {}", comment.getId(), canView);
                } else {
                    // 评论为公开，所有人都可以看到（包括未登录用户）
                    canView = true;
                    logger.debug("Comment {} is public, canView: {}", comment.getId(), canView);
                }
            }
            
            if (canView) {
                filteredComments.add(comment);
            }
        }

        logger.debug("Filtered to {} visible comments", filteredComments.size());
        return filteredComments;
    }

    /**
     * 根据用户ID查找所有评论（包括软删除的），用于管理员查看
     * @param userId 用户ID
     * @return 所有评论列表（包括软删除的）
     */
    @Transactional(readOnly = true)
    public List<Comment> findByUserIdIncludingDeleted(Long userId) {
        return commentRepository.findByUserIdWithContentAndDeletedByIncludingDeleted(userId);
    }

    /**
     * 设置评论的隐私状态
     * @param commentId 评论ID
     * @param isPrivate 是否私密
     * @param currentUser 当前用户（用于权限验证）
     * @return 是否设置成功
     */
    @Transactional
    public boolean setCommentPrivacy(Long commentId, boolean isPrivate, User currentUser) {
        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            return false;
        }

        // 权限验证：只有评论作者和管理员可以设置隐私状态
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        boolean isOwner = comment.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            return false;
        }

        comment.setPrivate(isPrivate);
        comment.setPrivacySetBy(currentUser);
        comment.setPrivacySetAt(java.time.LocalDateTime.now());

        commentRepository.save(comment);
        logger.debug("Comment {} privacy set to {} by {}", commentId, isPrivate, currentUser.getUsername());
        return true;
    }

    /**
     * 批量设置用户所有评论的隐私状态
     * @param userId 用户ID
     * @param isPrivate 是否私密
     * @param currentUser 当前用户（用于权限验证）
     * @return 更新的评论数量
     */
    @Transactional
    public int setAllCommentsPrivacyForUser(Long userId, boolean isPrivate, User currentUser) {
        // 权限验证：只有用户本人和管理员可以批量设置隐私状态
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        boolean isOwner = currentUser.getId().equals(userId);
        
        if (!isAdmin && !isOwner) {
            throw new SecurityException("You don't have permission to modify this user's comment privacy settings");
        }

        // 获取用户的所有评论（包括软删除的）
        List<Comment> userComments = commentRepository.findByUserIdWithContentAndDeletedByIncludingDeleted(userId);
        
        int updatedCount = 0;
        for (Comment comment : userComments) {
            // 只更新未删除的评论
            if (comment.getDeletedAt() == null) {
                comment.setPrivate(isPrivate);
                comment.setPrivacySetBy(currentUser);
                comment.setPrivacySetAt(java.time.LocalDateTime.now());
                commentRepository.save(comment);
                updatedCount++;
            }
        }
        
        return updatedCount;
    }

    /**
     * 设置评论的状态（管理员功能）
     * @param commentId 评论ID
     * @param status 状态值（0=正常，1=管理员设置隐藏）
     * @param currentUser 当前用户（必须是管理员）
     * @return 更新后的评论对象
     */
    @Transactional
    public Comment setCommentStatus(Long commentId, int status, User currentUser) {
        // 权限验证：只有管理员可以设置评论状态
        if (!"ADMIN".equals(currentUser.getRole())) {
            throw new SecurityException("Only administrators can modify comment status");
        }

        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            throw new IllegalArgumentException("Comment not found with id: " + commentId);
        }

        comment.setStatus(status);
        return commentRepository.save(comment);
    }


    /**
     * 根据流派ID列表查找评论（包括软删除的），用于版主查看
     * @param schoolIds 流派ID列表
     * @return 所有评论列表（包括软删除的）
     */
    @Transactional(readOnly = true)
    public List<Comment> findBySchoolIdsIncludingDeleted(List<Long> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return new ArrayList<>();
        }
        return commentRepository.findBySchoolIdsIncludingDeletedOrderByCreatedAtDesc(schoolIds);
    }
}