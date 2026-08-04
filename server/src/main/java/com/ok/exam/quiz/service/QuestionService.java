package com.ok.exam.quiz.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.dto.request.AnswerOptionRequest;
import com.ok.dto.request.teacher.CreateQuestionRequest;
import com.ok.dto.response.student.AnswerOptionStudentResponse;
import com.ok.dto.response.student.QuestionStudentResponse;
import com.ok.dto.response.teacher.AnswerOptionManagementResponse;
import com.ok.dto.response.teacher.QuestionManagementResponse;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.service.ExamService;
import com.ok.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository repository;
    private final ExamService examService;
    private final QuizSubmissionService submissionService;

    @Transactional
    public QuestionManagementResponse create(Long examId, CreateQuestionRequest request, String email) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity user = examService.currentUser(email);
        examService.requireOwnerOrAdmin(exam, user);
        requireDraftQuiz(exam);
        validateOptions(request.options());
        QuestionEntity question = new QuestionEntity(
                exam,
                request.content().trim(),
                QuestionType.MULTIPLE_CHOICE,
                BigDecimal.valueOf(request.points()),
                nextQuestionOrder(examId));
        IntStream.range(0, request.options().size()).forEach(index -> {
            AnswerOptionRequest option = request.options().get(index);
            question.addOption(new QuestionOptionEntity(
                    option.content().trim(),
                    option.correct(),
                    index + 1));
        });
        return management(repository.save(question));
    }

    @Transactional
    public QuestionManagementResponse update(Long id, CreateQuestionRequest request, String email) {
        QuestionEntity question = find(id);
        examService.requireOwnerOrAdmin(question.getExam(), examService.currentUser(email));
        requireDraftQuiz(question.getExam());
        validateOptions(request.options());
        question.update(request.content().trim(), BigDecimal.valueOf(request.points()));
        question.replaceOptions(IntStream.range(0, request.options().size())
                .mapToObj(index -> {
                    AnswerOptionRequest option = request.options().get(index);
                    return new QuestionOptionEntity(
                            option.content().trim(),
                            option.correct(),
                            index + 1);
                })
                .toList());
        return management(question);
    }

    @Transactional
    public void delete(Long id, String email) {
        QuestionEntity question = find(id);
        examService.requireOwnerOrAdmin(question.getExam(), examService.currentUser(email));
        requireDraftQuiz(question.getExam());
        repository.delete(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionManagementResponse> managementList(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        examService.requireOwnerOrAdmin(exam, examService.currentUser(email));
        requireQuiz(exam);
        return repository.findByExamIdOrderById(examId).stream().map(this::management).toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionStudentResponse> studentList(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity user = examService.currentUser(email);
        if (user.getRole() != Role.STUDENT) throw forbidden("Chỉ học sinh sử dụng nội dung đề này");
        requireQuiz(exam);
        submissionService.requireActiveAttempt(examId, user);
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStatus() != ExamStatus.PUBLISHED || now.isBefore(exam.getStartTime()) || now.isAfter(exam.getDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài kiểm tra chưa mở hoặc đã kết thúc");
        }
        return repository.findByExamIdOrderById(examId).stream().map(this::student).toList();
    }

    private QuestionEntity find(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi"));
    }

    private void requireDraftQuiz(ExamEntity exam) {
        requireQuiz(exam);
        if (exam.getStatus() != ExamStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ sửa câu hỏi khi đề ở trạng thái DRAFT");
    }

    private void requireQuiz(ExamEntity exam) {
        if (exam.getType() != ExamType.MULTIPLE_CHOICE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đây không phải đề trắc nghiệm");
    }

    private void validateOptions(List<AnswerOptionRequest> options) {
        long correctCount = options.stream().filter(AnswerOptionRequest::correct).count();
        if (options.size() < 2 || correctCount != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mỗi câu phải có ít nhất 2 lựa chọn và đúng 1 đáp án đúng");
        }
    }

    private int nextQuestionOrder(Long examId) {
        return repository.findByExamIdOrderById(examId).stream()
                .map(QuestionEntity::getQuestionOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private QuestionManagementResponse management(QuestionEntity q) {
        return new QuestionManagementResponse(q.getId(), q.getExam().getId(), q.getContent(), q.getMaxScore().doubleValue(),
                q.getOptions().stream().map(o -> new AnswerOptionManagementResponse(o.getId(), o.getContent(), o.isCorrect())).toList());
    }

    private QuestionStudentResponse student(QuestionEntity q) {
        return new QuestionStudentResponse(q.getId(), q.getContent(), q.getMaxScore().doubleValue(),
                q.getOptions().stream().map(o -> new AnswerOptionStudentResponse(o.getId(), o.getContent())).toList());
    }

    private ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
}
