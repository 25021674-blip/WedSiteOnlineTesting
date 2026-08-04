package com.ok.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ok.domain.enums.QuestionType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_exam_order",
                columnNames = {"exam_id", "question_order"}
        )
)
public class QuestionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("optionOrder ASC")
    private List<QuestionOptionEntity> options = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

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

    public void update(String content, BigDecimal maxScore) {
        this.content = content;
        this.maxScore = maxScore;
    }

    public void replaceOptions(List<QuestionOptionEntity> replacements) {
        options.clear();
        replacements.forEach(this::addOption);
    }

    public void addOption(QuestionOptionEntity option) {
        option.assignToQuestion(this);
        options.add(option);
    }

    public void removeOption(QuestionOptionEntity option) {
        options.remove(option);
        option.assignToQuestion(null);
    }

    public void changeOrder(Integer newOrder) {
        this.questionOrder = newOrder;
    }
}
