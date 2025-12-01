package com.philosophy.repository;

import com.philosophy.model.ContentTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, Long> {

    /**
     * 根据内容ID和语言代码查找翻译
     */
    Optional<ContentTranslation> findByContentIdAndLanguageCode(Long contentId, String languageCode);

    /**
     * 根据内容ID列表和语言代码查找所有翻译
     */
    List<ContentTranslation> findByContentIdInAndLanguageCode(List<Long> contentIds, String languageCode);

    /**
     * 根据语言代码查找所有翻译
     */
    List<ContentTranslation> findByLanguageCode(String languageCode);

    /**
     * 检查指定内容和语言是否已有翻译
     */
    boolean existsByContentIdAndLanguageCode(Long contentId, String languageCode);

    /**
     * 根据内容ID删除所有翻译
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ContentTranslation ct WHERE ct.content.id = :contentId")
    void deleteByContentId(@Param("contentId") Long contentId);

    /**
     * 根据内容ID和语言代码删除翻译
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ContentTranslation ct WHERE ct.content.id = :contentId AND ct.languageCode = :languageCode")
    void deleteByContentIdAndLanguageCode(@Param("contentId") Long contentId, @Param("languageCode") String languageCode);

    /**
     * 查找所有内容的英文翻译（包括没有翻译的内容）
     */
    @Query("SELECT c.id as contentId, c.content as chineseContent, " +
           "COALESCE(ct.contentEn, c.content) as displayContent " +
           "FROM Content c " +
           "LEFT JOIN ContentTranslation ct ON c.id = ct.content.id AND ct.languageCode = :languageCode " +
           "ORDER BY c.id")
    List<Object[]> findContentsWithTranslation(@Param("languageCode") String languageCode);

    /**
     * 根据流派ID查找内容的翻译
     */
    @Query("SELECT c.id as contentId, c.content as chineseContent, " +
           "COALESCE(ct.contentEn, c.content) as displayContent " +
           "FROM Content c " +
           "LEFT JOIN ContentTranslation ct ON c.id = ct.content.id AND ct.languageCode = :languageCode " +
           "WHERE c.school.id = :schoolId " +
           "ORDER BY c.id")
    List<Object[]> findContentsBySchoolIdWithTranslation(@Param("schoolId") Long schoolId, @Param("languageCode") String languageCode);

    /**
     * 根据流派ID列表查找内容的翻译
     */
    @Query("SELECT c.id as contentId, c.content as chineseContent, " +
           "COALESCE(ct.contentEn, c.content) as displayContent " +
           "FROM Content c " +
           "LEFT JOIN ContentTranslation ct ON c.id = ct.content.id AND ct.languageCode = :languageCode " +
           "WHERE c.school.id IN :schoolIds " +
           "ORDER BY c.id")
    List<Object[]> findContentsBySchoolIdsWithTranslation(@Param("schoolIds") List<Long> schoolIds, @Param("languageCode") String languageCode);
}
