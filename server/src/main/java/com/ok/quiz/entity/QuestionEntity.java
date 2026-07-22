package com.ok.quiz.entity;

import java.util.ArrayList;
import java.util.List;
import com.ok.entity.ExamEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "questions")
public class QuestionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private Double points;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnswerOptionEntity> options = new ArrayList<>();

    public QuestionEntity(ExamEntity exam, String content, double points) {
        this.exam = exam;
        this.content = content;
        this.points = points;
    }

    public void update(String content, double points) {
        this.content = content;
        this.points = points;
    }

    public void replaceOptions(List<AnswerOptionEntity> replacements) {
        options.clear();
        replacements.forEach(this::addOption);
    }

    public void addOption(AnswerOptionEntity option) {
        option.attachTo(this);
        options.add(option);
    }
}
