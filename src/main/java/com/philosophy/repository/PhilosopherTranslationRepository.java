package com.philosophy.repository;

import com.philosophy.model.PhilosopherTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhilosopherTranslationRepository extends JpaRepository<PhilosopherTranslation, Long> {

    /**
     * 根据哲学家ID和语言代码查找翻译
     */
    Optional<PhilosopherTranslation> findByPhilosopherIdAndLanguageCode(Long philosopherId, String languageCode);

    /**
     * 根据哲学家ID列表和语言代码查找所有翻译
     */
    List<PhilosopherTranslation> findByPhilosopherIdInAndLanguageCode(List<Long> philosopherIds, String languageCode);

    /**
     * 根据语言代码查找所有翻译
     */
    List<PhilosopherTranslation> findByLanguageCode(String languageCode);

    /**
     * 检查指定哲学家和语言是否已有翻译
     */
    boolean existsByPhilosopherIdAndLanguageCode(Long philosopherId, String languageCode);

    /**
     * 根据哲学家ID删除所有翻译
     */
    void deleteByPhilosopherId(Long philosopherId);

    /**
     * 根据哲学家ID和语言代码删除翻译
     */
    void deleteByPhilosopherIdAndLanguageCode(Long philosopherId, String languageCode);

    /**
     * 查找所有哲学家的英文翻译（包括没有翻译的哲学家）
     */
    @Query("SELECT p.id as philosopherId, p.name as chineseName, " +
           "COALESCE(pt.nameEn, p.name) as displayName, " +
           "COALESCE(pt.biographyEn, p.bio) as displayBiography " +
           "FROM Philosopher p " +
           "LEFT JOIN PhilosopherTranslation pt ON p.id = pt.philosopher.id AND pt.languageCode = :languageCode " +
           "ORDER BY p.name")
    List<Object[]> findPhilosophersWithTranslation(@Param("languageCode") String languageCode);
}
