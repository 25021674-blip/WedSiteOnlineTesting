package com.ok.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(
        name = "student_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_answer_attempt_question",
                columnNames = {"attempt_id", "question_id"}
        )
)
public class StudentAnswerEntityDemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttemptEntity attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOptionEntity selectedOption;

    @Column(name = "essay_answer", columnDefinition = "TEXT")
    private String essayAnswer;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "is_correct")
    private Boolean correct;

    public StudentAnswerEntityDemo(
            ExamAttemptEntity attempt,
            QuestionEntity question,
            QuestionOptionEntity selectedOption,
            String essayAnswer
    ) {
        this.attempt = attempt;
        this.question = question;
        this.selectedOption = selectedOption;
        this.essayAnswer = essayAnswer;
    }
}
