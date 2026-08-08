package com.ok.exam.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.Role;
import com.ok.dto.response.QuizAttemptResponse;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.service.ExamService;
import com.ok.entity.QuizSubmissionEntity;
import com.ok.repository.QuestionRepository;
import com.ok.repository.QuizSubmissionAnswerRepository;
import com.ok.repository.QuizSubmissionRepository;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class QuizSubmissionServiceTests {

    @Mock QuizSubmissionRepository submissionRepository;
    @Mock QuizSubmissionAnswerRepository answerRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ExamService examService;
    @org.mockito.Spy Clock clock = Clock.systemDefaultZone();
    @InjectMocks QuizSubmissionService service;

    @Test
    void startUsesCommonDeadlineWhenItComesBeforePersonalDuration() {
        UserEntity teacher = new UserEntity("Teacher", "teacher@example.com", "password", Role.TEACHER);
        UserEntity student = new UserEntity("Student", "student@example.com", "password", Role.STUDENT);
        LocalDateTime commonDeadline = LocalDateTime.now().plusMinutes(10);
        ZoneId zone = ZoneId.systemDefault();
        ExamEntity exam = new ExamEntity(teacher, "Quiz", null,
                LocalDateTime.now().minusMinutes(5).atZone(zone).toInstant(),
                commonDeadline.atZone(zone).toInstant(), 30, BigDecimal.TEN,
                ExamType.MULTIPLE_CHOICE);
        exam.changeStatus(ExamStatus.PUBLISHED);

        when(examService.findExam(1L)).thenReturn(exam);
        when(examService.currentUser("student@example.com")).thenReturn(student);
        when(submissionRepository.findByExamIdAndStudentId(1L, null)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(QuizSubmissionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuizAttemptResponse response = service.start(1L, "student@example.com");

        assertThat(response.status()).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(response.expiresAt()).isEqualTo(commonDeadline);
        assertThat(response.startedAt()).isBefore(response.expiresAt());
    }

    @Test
    void attemptTransitionsToSubmittedAndAutoSubmitted() {
        UserEntity teacher = new UserEntity("Teacher", "teacher@example.com", "password", Role.TEACHER);
        UserEntity student = new UserEntity("Student", "student@example.com", "password", Role.STUDENT);
        ZoneId zone = ZoneId.systemDefault();
        ExamEntity exam = new ExamEntity(teacher, "Quiz", null,
                LocalDateTime.now().minusMinutes(1).atZone(zone).toInstant(),
                LocalDateTime.now().plusHours(1).atZone(zone).toInstant(), 30,
                BigDecimal.TEN, ExamType.MULTIPLE_CHOICE);
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime expiresAt = startedAt.plusMinutes(30);

        QuizSubmissionEntity submitted = new QuizSubmissionEntity(exam, student, startedAt, expiresAt);
        submitted.submit(8, 10, startedAt.plusMinutes(12));
        assertThat(submitted.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isEqualTo(startedAt.plusMinutes(12));

        QuizSubmissionEntity automatic = new QuizSubmissionEntity(exam, student, startedAt, expiresAt);
        automatic.autoSubmit(6, 10);
        assertThat(automatic.getStatus()).isEqualTo(ExamAttemptStatus.AUTO_SUBMITTED);
        assertThat(automatic.getSubmittedAt()).isEqualTo(expiresAt);
    }
}
