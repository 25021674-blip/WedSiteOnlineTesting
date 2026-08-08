package com.ok.exam.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.dto.response.student.StudentExamScreenResponse;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentExamRepository;
import com.ok.repository.StudentQuestionRepository;
import com.ok.repository.StudentUserRepository;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class StudentExamAttemptServiceDemoTest {

    @Mock
    private StudentUserRepository studentUserRepository;

    @Mock
    private StudentExamRepository examRepository;

    @Mock
    private StudentExamAttemptRepository attemptRepository;

    @Mock
    private StudentQuestionRepository questionRepository;

    @Mock
    private StudentAnswerRepository answerRepository;

    private StudentExamAttemptService service;

    @BeforeEach
    void setUp() {
        service = new StudentExamAttemptService(
                studentUserRepository,
                examRepository,
                attemptRepository,
                questionRepository,
                answerRepository
        );
    }

    @Test
    void shouldCreateNewAttemptAndReturnEmptyProgress() {
        UserEntity student = createStudent();
        ExamEntity exam = createExam();
        QuestionEntity question = createMultipleChoiceQuestion(exam);

        when(studentUserRepository.findByEmailForUpdate(student.getEmail()))
                .thenReturn(Optional.of(student));
        when(examRepository.findById(exam.getId()))
                .thenReturn(Optional.of(exam));
        when(attemptRepository
                .findFirstByExam_IdAndStudent_IdOrderByStartedAtDesc(
                        exam.getId(),
                        student.getId()
                ))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(ExamAttemptEntity.class)))
                .thenAnswer(invocation -> {
                    ExamAttemptEntity attempt = invocation.getArgument(0);
                    ReflectionTestUtils.setField(attempt, "id", 101L);
                    return attempt;
                });
        when(questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttempt_Id(101L))
                .thenReturn(List.of());

        StudentExamScreenResponse response = service.startOrResume(
                exam.getId(),
                student.getEmail()
        );

        assertThat(response.attemptId()).isEqualTo(101L);
        assertThat(response.status().name()).isEqualTo("IN_PROGRESS");
        assertThat(response.progress().answeredCount()).isZero();
        assertThat(response.progress().totalQuestions()).isEqualTo(1);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().options()).hasSize(2);
        assertThat(response.questions().getFirst().answer()).isNull();
    }

    @Test
    void shouldResumeAttemptAndRestoreSavedAnswer() {
        UserEntity student = createStudent();
        ExamEntity exam = createExam();
        QuestionEntity question = createMultipleChoiceQuestion(exam);
        QuestionOptionEntity selectedOption = question.getOptions().get(1);

        ExamAttemptEntity attempt = new ExamAttemptEntity(
                exam,
                student,
                Instant.now().plusSeconds(1800)
        );
        ReflectionTestUtils.setField(attempt, "id", 101L);

        StudentAnswerEntity answer = new StudentAnswerEntity(
                attempt,
                question,
                selectedOption,
                null
        );
        ReflectionTestUtils.setField(answer, "clientRevision", 3L);

        when(studentUserRepository.findByEmailForUpdate(student.getEmail()))
                .thenReturn(Optional.of(student));
        when(examRepository.findById(exam.getId()))
                .thenReturn(Optional.of(exam));
        when(attemptRepository
                .findFirstByExam_IdAndStudent_IdOrderByStartedAtDesc(
                        exam.getId(),
                        student.getId()
                ))
                .thenReturn(Optional.of(attempt));
        when(questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttempt_Id(attempt.getId()))
                .thenReturn(List.of(answer));

        StudentExamScreenResponse response = service.startOrResume(
                exam.getId(),
                student.getEmail()
        );

        assertThat(response.attemptId()).isEqualTo(101L);
        assertThat(response.progress().answeredCount()).isEqualTo(1);
        assertThat(response.progress().answeredQuestionIds())
                .containsExactly(question.getId());
        assertThat(response.questions().getFirst().answer().selectedOptionId())
                .isEqualTo(selectedOption.getId());
        assertThat(response.questions().getFirst().answer().clientRevision())
                .isEqualTo(3L);
    }

    @Test
    void shouldAutoSubmitExpiredAttempt() {
        UserEntity student = createStudent();
        ExamEntity exam = createExam();

        ExamAttemptEntity attempt = new ExamAttemptEntity(
                exam,
                student,
                Instant.now().minusSeconds(1)
        );
        ReflectionTestUtils.setField(attempt, "id", 101L);

        when(studentUserRepository.findByEmailForUpdate(student.getEmail()))
                .thenReturn(Optional.of(student));
        when(examRepository.findById(exam.getId()))
                .thenReturn(Optional.of(exam));
        when(attemptRepository
                .findFirstByExam_IdAndStudent_IdOrderByStartedAtDesc(
                        exam.getId(),
                        student.getId()
                ))
                .thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.startOrResume(
                exam.getId(),
                student.getEmail()
        )).isInstanceOf(ResponseStatusException.class);

        verify(attemptRepository).markAutoSubmitted(
                eq(attempt.getId()),
                eq(ExamAttemptStatus.IN_PROGRESS),
                eq(ExamAttemptStatus.AUTO_SUBMITTED),
                any(Instant.class)
        );
    }

    private UserEntity createStudent() {
        UserEntity student = new UserEntity(
                "Nguyen Van A",
                "student@example.com",
                "encoded-password",
                Role.STUDENT
        );
        ReflectionTestUtils.setField(student, "id", 7L);
        return student;
    }

    private ExamEntity createExam() {
        UserEntity teacher = new UserEntity(
                "Teacher",
                "teacher@example.com",
                "encoded-password",
                Role.TEACHER
        );

        ExamEntity exam = new ExamEntity(
                teacher,
                "Kiểm tra Java",
                "Kiểm tra giữa kỳ",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                60,
                BigDecimal.TEN,
                ExamType.MULTIPLE_CHOICE
        );
        ReflectionTestUtils.setField(exam, "id", 15L);
        return exam;
    }

    private QuestionEntity createMultipleChoiceQuestion(ExamEntity exam) {
        QuestionEntity question = new QuestionEntity(
                exam,
                "Java là gì?",
                QuestionType.MULTIPLE_CHOICE,
                BigDecimal.ONE,
                1
        );
        ReflectionTestUtils.setField(question, "id", 12L);

        QuestionOptionEntity firstOption = new QuestionOptionEntity(
                "Hệ điều hành",
                false,
                1
        );
        ReflectionTestUtils.setField(firstOption, "id", 24L);

        QuestionOptionEntity secondOption = new QuestionOptionEntity(
                "Ngôn ngữ lập trình",
                true,
                2
        );
        ReflectionTestUtils.setField(secondOption, "id", 25L);

        question.addOption(firstOption);
        question.addOption(secondOption);
        return question;
    }
}
