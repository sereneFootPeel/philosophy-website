package com.philosophy.repository;

import com.philosophy.model.Philosopher;
import com.philosophy.util.SearchNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PhilosopherRepositorySearchTest {

    @Autowired
    private PhilosopherRepository philosopherRepository;

    @Test
    void search_shouldMatch_AJ_withoutDots_to_AJ_withDots_andSpaces() {
        Philosopher p = new Philosopher();
        p.setName("A.J. 艾耶尔");
        p.setNameEn("A.J. Ayer");
        philosopherRepository.saveAndFlush(p);

        String query = "AJ艾耶尔";
        List<Philosopher> results = philosopherRepository.searchByNameOrNameEn(
                query,
                SearchNormalizer.normalize(query)
        );

        assertThat(results)
                .extracting(Philosopher::getName)
                .contains("A.J. 艾耶尔");
    }
}


