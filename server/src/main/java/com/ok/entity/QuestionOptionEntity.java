package com.ok.entity;

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
        name = "question_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_option_question_order",
                columnNames = {"question_id", "option_order"}
        )
)
public class QuestionOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    public QuestionOptionEntity(
            String content,
            boolean correct,
            Integer optionOrder
    ) {
        this.content = content;
        this.correct = correct;
        this.optionOrder = optionOrder;
    }

    void assignToQuestion(QuestionEntity question) {
        this.question = question;
    }
}
