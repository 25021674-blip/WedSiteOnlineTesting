package com.ok.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "answer_options")
public class AnswerOptionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private boolean correct;

    public AnswerOptionEntity(String content, boolean correct) {
        this.content = content;
        this.correct = correct;
    }

    void attachTo(QuestionEntity question) {
        this.question = question;
    }
}
