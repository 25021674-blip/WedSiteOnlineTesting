package com.ok.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "quiz_submission_answers")
public class QuizSubmissionAnswerEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private QuizSubmissionEntity submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_option_id", nullable = false)
    private AnswerOptionEntity selectedOption;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private Double awardedPoints;

    public QuizSubmissionAnswerEntity(QuizSubmissionEntity submission, QuestionEntity question,
            AnswerOptionEntity selectedOption, boolean correct, double awardedPoints) {
        this.submission = submission;
        this.question = question;
        this.selectedOption = selectedOption;
        this.correct = correct;
        this.awardedPoints = awardedPoints;
    }
}
