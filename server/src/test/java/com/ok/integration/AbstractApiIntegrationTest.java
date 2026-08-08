package com.ok.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.ok.WedSiteOnlineTestingApplication;
import com.ok.auth.service.JwtService;
import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.QuestionEntity;
import com.ok.exam.quiz.service.QuizSubmissionService;
import com.ok.repository.EssayAssignmentFileRepository;
import com.ok.repository.EssaySubmissionRepository;
import com.ok.repository.ExamRepository;
import com.ok.repository.QuestionRepository;
import com.ok.repository.QuizSubmissionAnswerRepository;
import com.ok.repository.QuizSubmissionRepository;
import com.ok.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = WedSiteOnlineTestingApplication.class)
@AutoConfigureMockMvc
@Import(AbstractApiIntegrationTest.FixedClockConfig.class)
@Tag("integration")
@Tag("api")
abstract class AbstractApiIntegrationTest {

    static final ZoneId TEST_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final Instant TEST_INSTANT = Instant.parse("2030-01-15T03:00:00Z");
    static final LocalDateTime NOW = LocalDateTime.ofInstant(TEST_INSTANT, TEST_ZONE);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected ExamRepository examRepository;
    @Autowired protected QuestionRepository questionRepository;
    @Autowired protected QuizSubmissionRepository quizSubmissionRepository;
    @Autowired protected QuizSubmissionAnswerRepository quizAnswerRepository;
    @Autowired protected EssaySubmissionRepository essaySubmissionRepository;
    @Autowired protected EssayAssignmentFileRepository assignmentFileRepository;
    @Autowired protected QuizSubmissionService quizSubmissionService;

    @BeforeEach
    void cleanDatabase() {
        quizAnswerRepository.deleteAllInBatch();
        quizSubmissionRepository.deleteAllInBatch();
        essaySubmissionRepository.deleteAllInBatch();
        assignmentFileRepository.deleteAllInBatch();
        questionRepository.deleteAll();
        questionRepository.flush();
        examRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected UserEntity user(String email, Role role) {
        return userRepository.save(new UserEntity(role.name() + " User", email,
                passwordEncoder.encode("Password123"), role));
    }

    protected String bearer(UserEntity user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    protected ExamEntity quiz(UserEntity owner, ExamStatus status) {
        return exam(owner, ExamType.MULTIPLE_CHOICE, status,
                NOW.minusMinutes(10), NOW.plusHours(2), 30);
    }

    protected ExamEntity essay(UserEntity owner, ExamStatus status) {
        return exam(owner, ExamType.ESSAY, status,
                NOW.minusMinutes(10), NOW.plusHours(2), null);
    }

    protected ExamEntity exam(UserEntity owner, ExamType type, ExamStatus status,
            LocalDateTime start, LocalDateTime deadline, Integer duration) {
        ExamEntity exam = new ExamEntity(owner, type.name() + " Exam", "Description",
                start.atZone(TEST_ZONE).toInstant(), deadline.atZone(TEST_ZONE).toInstant(),
                duration == null ? 60 : duration, BigDecimal.TEN, type);
        exam.changeStatus(status);
        return examRepository.save(exam);
    }

    protected QuestionEntity question(ExamEntity exam, double points) {
        int questionOrder = questionRepository.findByExamIdOrderById(exam.getId()).size() + 1;
        QuestionEntity question = new QuestionEntity(exam, "Question content",
                QuestionType.MULTIPLE_CHOICE, java.math.BigDecimal.valueOf(points), questionOrder);
        question.addOption(new QuestionOptionEntity("Correct", true, 1));
        question.addOption(new QuestionOptionEntity("Wrong", false, 2));
        return questionRepository.saveAndFlush(question);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedTestClock() {
            return Clock.fixed(TEST_INSTANT, TEST_ZONE);
        }
    }
}
