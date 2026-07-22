package com.ok.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ok.dto.QuestionType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(
        name = "questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_exam_order",
                columnNames = {"exam_id", "question_order"}
        )
)
public class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @OneToMany(
            mappedBy = "question",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("optionOrder ASC")
    private List<QuestionOptionEntity> options = new ArrayList<>();

    public QuestionEntity(
            ExamEntity exam,
            String content,
            QuestionType questionType,
            BigDecimal maxScore,
            Integer questionOrder
    ) {
        this.exam = exam;
        this.content = content;
        this.questionType = questionType;
        this.maxScore = maxScore;
        this.questionOrder = questionOrder;
    }

    public void addOption(QuestionOptionEntity option) {
        options.add(option);
        option.assignToQuestion(this);
    }

    public void removeOption(QuestionOptionEntity option) {
        options.remove(option);
        option.assignToQuestion(null);
    }
}
