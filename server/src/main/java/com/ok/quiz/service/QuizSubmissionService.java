package com.ok.quiz.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.dto.*;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.quiz.entity.*;
import com.ok.exam.service.ExamService;
import com.ok.repository.QuestionRepository;
import com.ok.repository.QuizSubmissionAnswerRepository;
import com.ok.repository.QuizSubmissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizSubmissionService {
    private final QuizSubmissionRepository submissionRepository;
    private final QuizSubmissionAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final ExamService examService;

    @Transactional
    public QuizResultResponse submit(Long examId, SubmitQuizRequest request, String email) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity student = examService.currentUser(email);
        requireOpenQuiz(exam, student);
        if (submissionRepository.existsByExamIdAndStudentId(examId, student.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã nộp bài trắc nghiệm này");
        }

        List<QuestionEntity> questions = questionRepository.findByExamIdOrderById(examId);
        if (questions.isEmpty()) throw invalid("Đề chưa có câu hỏi");
        Map<Long, SelectedAnswerRequest> selected = indexAnswers(request.answers());
        if (selected.size() != questions.size()) throw invalid("Bạn phải trả lời đầy đủ mỗi câu đúng một lần");

        double total = questions.stream().mapToDouble(QuestionEntity::getPoints).sum();
        double score = 0;
        Map<QuestionEntity, AnswerOptionEntity> chosen = new HashMap<>();
        for (QuestionEntity question : questions) {
            SelectedAnswerRequest answer = selected.get(question.getId());
            if (answer == null) throw invalid("Đáp án không thuộc đề kiểm tra");
            AnswerOptionEntity option = question.getOptions().stream()
                    .filter(o -> o.getId().equals(answer.selectedOptionId())).findFirst()
                    .orElseThrow(() -> invalid("Lựa chọn không thuộc câu hỏi " + question.getId()));
            chosen.put(question, option);
            if (option.isCorrect()) score += question.getPoints();
        }

        QuizSubmissionEntity submission = submissionRepository.save(
                new QuizSubmissionEntity(exam, student, score, total));
        List<QuizSubmissionAnswerEntity> savedAnswers = chosen.entrySet().stream().map(entry -> {
            boolean correct = entry.getValue().isCorrect();
            return new QuizSubmissionAnswerEntity(submission, entry.getKey(), entry.getValue(), correct,
                    correct ? entry.getKey().getPoints() : 0);
        }).toList();
        answerRepository.saveAll(savedAnswers);
        return response(submission, savedAnswers);
    }

    @Transactional(readOnly = true)
    public QuizResultResponse getMine(Long examId, String email) {
        UserEntity student = examService.currentUser(email);
        QuizSubmissionEntity submission = submissionRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bạn chưa nộp bài này"));
        return response(submission, answerRepository.findBySubmissionIdOrderById(submission.getId()));
    }

    private Map<Long, SelectedAnswerRequest> indexAnswers(List<SelectedAnswerRequest> answers) {
        Map<Long, SelectedAnswerRequest> result = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (SelectedAnswerRequest answer : answers) {
            if (!seen.add(answer.questionId())) throw invalid("Một câu hỏi không được trả lời nhiều lần");
            result.put(answer.questionId(), answer);
        }
        return result;
    }

    private void requireOpenQuiz(ExamEntity exam, UserEntity student) {
        if (student.getRole() != Role.STUDENT) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ học sinh được nộp bài");
        if (exam.getType() != ExamType.MULTIPLE_CHOICE) throw invalid("Đây không phải đề trắc nghiệm");
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStatus() != ExamStatus.PUBLISHED || now.isBefore(exam.getStartTime()) || now.isAfter(exam.getDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài kiểm tra chưa mở hoặc đã kết thúc");
        }
    }

    private QuizResultResponse response(QuizSubmissionEntity submission, List<QuizSubmissionAnswerEntity> answers) {
        return new QuizResultResponse(submission.getId(), submission.getExam().getId(), submission.getStudent().getId(),
                submission.getScore(), submission.getTotalPoints(), submission.getSubmittedAt(),
                answers.stream().map(a -> new QuizAnswerResultResponse(a.getQuestion().getId(),
                        a.getSelectedOption().getId(), a.isCorrect(), a.getAwardedPoints())).toList());
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
