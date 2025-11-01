package com.philosophy.repository;

import com.philosophy.model.SchoolTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolTranslationRepository extends JpaRepository<SchoolTranslation, Long> {

    /**
     * 根据流派ID和语言代码查找翻译
     */
    Optional<SchoolTranslation> findBySchoolIdAndLanguageCode(Long schoolId, String languageCode);

    /**
     * 根据流派ID列表和语言代码查找所有翻译
     */
    List<SchoolTranslation> findBySchoolIdInAndLanguageCode(List<Long> schoolIds, String languageCode);

    /**
     * 根据语言代码查找所有翻译
     */
    List<SchoolTranslation> findByLanguageCode(String languageCode);

    /**
     * 检查指定流派和语言是否已有翻译
     */
    boolean existsBySchoolIdAndLanguageCode(Long schoolId, String languageCode);

    /**
     * 根据流派ID删除所有翻译
     */
    void deleteBySchoolId(Long schoolId);

    /**
     * 根据流派ID和语言代码删除翻译
     */
    void deleteBySchoolIdAndLanguageCode(Long schoolId, String languageCode);

    /**
     * 查找所有流派的英文翻译（包括没有翻译的流派）
     */
    @Query("SELECT s.id as schoolId, s.name as chineseName, " +
           "COALESCE(st.nameEn, s.name) as displayName, " +
           "COALESCE(st.descriptionEn, s.description) as displayDescription " +
           "FROM School s " +
           "LEFT JOIN SchoolTranslation st ON s.id = st.school.id AND st.languageCode = :languageCode " +
           "ORDER BY s.name")
    List<Object[]> findSchoolsWithTranslation(@Param("languageCode") String languageCode);
}
