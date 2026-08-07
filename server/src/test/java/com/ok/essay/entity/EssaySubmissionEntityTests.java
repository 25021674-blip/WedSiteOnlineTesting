package com.ok.essay.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("unit")
class EssaySubmissionEntityTests {

    @Test
    void gradeWithOnlyScoreStoresScoreAndKeepsFeedbackNull() {
        EssaySubmissionEntity submission = new EssaySubmissionEntity(null, null, "original.pdf", "stored.pdf", "/tmp", 123L);

        submission.grade(8.5);

        assertThat(submission.getScore()).isEqualTo(8.5);
        assertThat(submission.getFeedback()).isNull();
    }
}
