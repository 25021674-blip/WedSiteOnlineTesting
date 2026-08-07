package com.ok.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ok.dto.common.ExamStatus;
import com.ok.dto.common.ExamType;
import com.ok.dto.common.QuizAttemptStatus;
import com.ok.dto.common.Role;
import com.ok.dto.student.QuizAttemptResponse;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.service.ExamService;
import com.ok.quiz.entity.QuizSubmissionEntity;
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
        ExamEntity exam = new ExamEntity("Quiz", null, ExamType.MULTIPLE_CHOICE,
                LocalDateTime.now().minusMinutes(5), commonDeadline, 30, teacher);
        exam.changeStatus(ExamStatus.PUBLISHED);

        when(examService.findExam(1L)).thenReturn(exam);
        when(examService.currentUser("student@example.com")).thenReturn(student);
        when(submissionRepository.findByExamIdAndStudentId(1L, null)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(QuizSubmissionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuizAttemptResponse response = service.start(1L, "student@example.com");

        assertThat(response.status()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        assertThat(response.expiresAt()).isEqualTo(commonDeadline);
        assertThat(response.startedAt()).isBefore(response.expiresAt());
    }

    @Test
    void attemptTransitionsToSubmittedAndAutoSubmitted() {
        UserEntity teacher = new UserEntity("Teacher", "teacher@example.com", "password", Role.TEACHER);
        UserEntity student = new UserEntity("Student", "student@example.com", "password", Role.STUDENT);
        ExamEntity exam = new ExamEntity("Quiz", null, ExamType.MULTIPLE_CHOICE,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1), 30, teacher);
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime expiresAt = startedAt.plusMinutes(30);

        QuizSubmissionEntity submitted = new QuizSubmissionEntity(exam, student, startedAt, expiresAt);
        submitted.submit(8, 10, startedAt.plusMinutes(12));
        assertThat(submitted.getStatus()).isEqualTo(QuizAttemptStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isEqualTo(startedAt.plusMinutes(12));

        QuizSubmissionEntity automatic = new QuizSubmissionEntity(exam, student, startedAt, expiresAt);
        automatic.autoSubmit(6, 10);
        assertThat(automatic.getStatus()).isEqualTo(QuizAttemptStatus.AUTO_SUBMITTED);
        assertThat(automatic.getSubmittedAt()).isEqualTo(expiresAt);
    }
}
