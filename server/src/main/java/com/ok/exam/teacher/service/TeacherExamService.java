package com.ok.exam.teacher.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.Role;
import com.ok.dto.response.teacher.TeacherExamDetailResponse;
import com.ok.dto.response.teacher.TeacherExamSubmissionResponse;
import com.ok.dto.response.teacher.TeacherExamSummaryResponse;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse.AnswerDetail;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse.OptionDetail;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse.QuestionDetail;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentQuestionRepository;
import com.ok.repository.TeacherExamRepository;
import com.ok.repository.UserRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class TeacherExamService {

    private final UserRepository userRepository;
    private final TeacherExamRepository teacherExamRepository;
    private final StudentExamAttemptRepository studentExamAttemptRepository;
    private final StudentQuestionRepository studentQuestionRepository;
    private final StudentAnswerRepository studentAnswerRepository;

    @Transactional(readOnly = true)
    public List<TeacherExamSummaryResponse> getExamSummaries(
            String authenticatedEmail
    ) {
        UserEntity teacher = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ giáo viên được xem danh sách bài kiểm tra"
            );
        }

        return teacherExamRepository
                .findSummariesByTeacherId(teacher.getId())
                .stream()
                .map(summary -> new TeacherExamSummaryResponse(
                        summary.getExamId(),
                        summary.getTitle(),
                        summary.getCompletedStudentCount()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherExamDetailResponse getExamDetail(
            Long examId,
            String authenticatedEmail
    ) {
        UserEntity teacher = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ giáo viên được xem chi tiết bài kiểm tra"
            );
        }

        return teacherExamRepository
                .findDetailByExamIdAndTeacherId(
                        examId,
                        teacher.getId()
                )
                .map(detail -> new TeacherExamDetailResponse(
                        detail.getExamId(),
                        detail.getTitle(),
                        detail.getType(),
                        detail.getCompletedStudentCount(),
                        detail.getCreatedAt(),
                        detail.getExpiresAt(),
                        detail.getDurationMinutes(),
                        detail.getMaxScore()
                ))
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài kiểm tra"
                ));
    }

    @Transactional(readOnly = true)
    public List<TeacherExamSubmissionResponse> getExamSubmissions(
            Long examId,
            String authenticatedEmail
    ) {
        UserEntity teacher = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ giáo viên được xem danh sách bài nộp"
            );
        }

        teacherExamRepository
                .findDetailByExamIdAndTeacherId(
                        examId,
                        teacher.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài kiểm tra"
                ));

        return teacherExamRepository
                .findSubmissionsByExamIdAndTeacherId(
                        examId,
                        teacher.getId()
                )
                .stream()
                .map(submission -> new TeacherExamSubmissionResponse(
                        submission.getAttemptId(),
                        submission.getStudentId(),
                        submission.getStudentName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherStudentAttemptDetailResponse getStudentAttemptDetail(
            Long examId,
            Long attemptId,
            String authenticatedEmail
    ) {
        UserEntity teacher = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ giáo viên được xem bài làm của học sinh"
            );
        }

        ExamAttemptEntity attempt = studentExamAttemptRepository
                .findSubmittedForTeacherReview(
                        examId,
                        attemptId,
                        teacher.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài làm đã nộp"
                ));

        List<QuestionEntity> questions = studentQuestionRepository
                .findByExam_IdOrderByQuestionOrderAsc(examId);
        List<StudentAnswerEntity> answers = studentAnswerRepository
                .findByAttempt_Id(attemptId);

        Map<Long, StudentAnswerEntity> answerByQuestionId = new HashMap<>();
        for (StudentAnswerEntity answer : answers) {
            answerByQuestionId.put(
                    answer.getQuestion().getId(),
                    answer
            );
        }

        List<QuestionDetail> questionDetails = questions
                .stream()
                .map(question -> createQuestionDetail(
                        question,
                        answerByQuestionId.get(question.getId())
                ))
                .toList();

        int screenExitCount = attempt.getScreenExitCount() == null
                ? 0
                : attempt.getScreenExitCount();

        return new TeacherStudentAttemptDetailResponse(
                attempt.getId(),
                attempt.getExam().getTitle(),
                attempt.getStudent().getId(),
                attempt.getStudent().getFullName(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                screenExitCount,
                attempt.getScore(),
                attempt.getExam().getMaxScore(),
                questionDetails
        );
    }

    private QuestionDetail createQuestionDetail(
            QuestionEntity question,
            StudentAnswerEntity answer
    ) {
        List<OptionDetail> options = question.getOptions()
                .stream()
                .map(this::createOptionDetail)
                .toList();

        AnswerDetail answerDetail = answer == null
                ? null
                : createAnswerDetail(answer);

        return new QuestionDetail(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getContent(),
                question.getMaxScore(),
                options,
                answerDetail
        );
    }

    private OptionDetail createOptionDetail(
            QuestionOptionEntity option
    ) {
        return new OptionDetail(
                option.getId(),
                option.getContent(),
                option.isCorrect(),
                option.getOptionOrder()
        );
    }

    private AnswerDetail createAnswerDetail(
            StudentAnswerEntity answer
    ) {
        Long selectedOptionId = answer.getSelectedOption() == null
                ? null
                : answer.getSelectedOption().getId();

        return new AnswerDetail(
                selectedOptionId,
                answer.getEssayAnswer(),
                answer.getScore(),
                answer.getCorrect()
        );
    }
}
