package com.philosophy.service;

import com.opencsv.CSVWriter;
import com.philosophy.model.*;
import com.philosophy.repository.*;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CsvExportService {

    private final CommentRepository commentRepository;
    private final ContentRepository contentRepository;
    private final ContentTranslationRepository contentTranslationRepository;
    private final LikeRepository likeRepository;
    private final ModeratorBlockRepository moderatorBlockRepository;
    private final PhilosopherRepository philosopherRepository;
    private final PhilosopherTranslationRepository philosopherTranslationRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolTranslationRepository schoolTranslationRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserContentEditRepository userContentEditRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserLoginInfoRepository userLoginInfoRepository;

    public CsvExportService(CommentRepository commentRepository,
                              ContentRepository contentRepository,
                              ContentTranslationRepository contentTranslationRepository,
                              LikeRepository likeRepository,
                              ModeratorBlockRepository moderatorBlockRepository,
                              PhilosopherRepository philosopherRepository,
                              PhilosopherTranslationRepository philosopherTranslationRepository,
                              SchoolRepository schoolRepository,
                              SchoolTranslationRepository schoolTranslationRepository,
                              UserRepository userRepository,
                              UserBlockRepository userBlockRepository,
                              UserContentEditRepository userContentEditRepository,
                              UserFollowRepository userFollowRepository,
                              UserLoginInfoRepository userLoginInfoRepository) {
        this.commentRepository = commentRepository;
        this.contentRepository = contentRepository;
        this.contentTranslationRepository = contentTranslationRepository;
        this.likeRepository = likeRepository;
        this.moderatorBlockRepository = moderatorBlockRepository;
        this.philosopherRepository = philosopherRepository;
        this.philosopherTranslationRepository = philosopherTranslationRepository;
        this.schoolRepository = schoolRepository;
        this.schoolTranslationRepository = schoolTranslationRepository;
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
        this.userContentEditRepository = userContentEditRepository;
        this.userFollowRepository = userFollowRepository;
        this.userLoginInfoRepository = userLoginInfoRepository;
    }

    public void exportAllDataToCsv(String directoryPath) throws IOException {
        exportToCsv(directoryPath + "/comments.csv", Comment.class, commentRepository.findAll());
        exportToCsv(directoryPath + "/contents.csv", Content.class, contentRepository.findAll());
        exportToCsv(directoryPath + "/content_translations.csv", ContentTranslation.class, contentTranslationRepository.findAll());
        exportToCsv(directoryPath + "/likes.csv", Like.class, likeRepository.findAll());
        exportToCsv(directoryPath + "/moderator_blocks.csv", ModeratorBlock.class, moderatorBlockRepository.findAll());
        exportToCsv(directoryPath + "/philosophers.csv", Philosopher.class, philosopherRepository.findAll());
        exportToCsv(directoryPath + "/philosopher_translations.csv", PhilosopherTranslation.class, philosopherTranslationRepository.findAll());
        exportToCsv(directoryPath + "/schools.csv", School.class, schoolRepository.findAll());
        exportToCsv(directoryPath + "/school_translations.csv", SchoolTranslation.class, schoolTranslationRepository.findAll());
        exportToCsv(directoryPath + "/users.csv", User.class, userRepository.findAll());
        exportToCsv(directoryPath + "/user_blocks.csv", UserBlock.class, userBlockRepository.findAll());
        exportToCsv(directoryPath + "/user_content_edits.csv", UserContentEdit.class, userContentEditRepository.findAll());
        exportToCsv(directoryPath + "/user_follows.csv", UserFollow.class, userFollowRepository.findAll());
        exportToCsv(directoryPath + "/user_login_info.csv", UserLoginInfo.class, userLoginInfoRepository.findAll());
        
        // 导出哲学家-学派关联表
        exportPhilosopherSchoolAssociations(directoryPath + "/philosopher_school.csv");
    }

    private <T> void exportToCsv(String fileName, Class<T> clazz, List<T> data) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(fileName))) {
            // Write header
            Field[] fields = clazz.getDeclaredFields();
            List<String> header = new ArrayList<>();
            for (Field field : fields) {
                header.add(field.getName());
            }
            writer.writeNext(header.toArray(new String[0]));

            // Write data
            for (T item : data) {
                List<String> row = new ArrayList<>();
                for (Field field : fields) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(item);
                        row.add(value != null ? value.toString() : "");
                    } catch (IllegalAccessException e) {
                        row.add("");
                    }
                }
                writer.writeNext(row.toArray(new String[0]));
            }
        }
    }

    private void exportPhilosopherSchoolAssociations(String fileName) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(fileName))) {
            // Write header
            writer.writeNext(new String[]{"philosopher_id", "school_id"});
            
            // Write data
            List<Philosopher> philosophers = philosopherRepository.findAll();
            for (Philosopher philosopher : philosophers) {
                for (School school : philosopher.getSchools()) {
                    writer.writeNext(new String[]{
                        philosopher.getId().toString(),
                        school.getId().toString()
                    });
                }
            }
        }
    }

    public void zipCsvFiles(String sourceDirPath, ZipOutputStream zos) throws IOException {
        File sourceDir = new File(sourceDirPath);
        for (File file : sourceDir.listFiles()) {
            zos.putNextEntry(new ZipEntry(file.getName()));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    public void cleanUp(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
