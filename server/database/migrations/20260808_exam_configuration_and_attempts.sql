-- Run this migration on MySQL before deploying the application version
-- that enables exam configuration and multiple attempts.

ALTER TABLE exams
    ADD COLUMN show_correct_answers_after_submit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN show_score_after_submit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 1,
    ADD COLUMN time_limit_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN require_fullscreen BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN track_tab_switches BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS exam_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recipient_exam_student
        UNIQUE (exam_id, student_id),
    CONSTRAINT fk_exam_recipient_exam
        FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_exam_recipient_student
        FOREIGN KEY (student_id) REFERENCES users (id)
);

-- Preserve the old behavior: published exams were visible to every
-- student before recipient-based access was introduced.
INSERT IGNORE INTO exam_recipients (
    exam_id,
    student_id,
    assigned_at
)
SELECT
    exam.id,
    student.id,
    NOW(6)
FROM exams exam
JOIN users student ON student.role = 'STUDENT'
WHERE exam.status IN ('PUBLISHED', 'CLOSED');

-- Existing published exams used to expose quiz score and correctness.
UPDATE exams
SET show_correct_answers_after_submit = TRUE,
    show_score_after_submit = TRUE
WHERE status IN ('PUBLISHED', 'CLOSED');

ALTER TABLE exam_attempts
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1;

ALTER TABLE exam_attempts
    MODIFY COLUMN score DECIMAL(10, 2) NULL;

ALTER TABLE quiz_submissions
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1;

ALTER TABLE essay_submissions
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX uk_attempt_exam_student_number
    ON exam_attempts (exam_id, student_id, attempt_number);

CREATE UNIQUE INDEX uk_quiz_submission_exam_student_number
    ON quiz_submissions (exam_id, student_id, attempt_number);

CREATE UNIQUE INDEX uk_essay_submission_exam_student_number
    ON essay_submissions (exam_id, student_id, attempt_number);

DELIMITER //

CREATE PROCEDURE drop_old_exam_student_unique_index(
    IN target_table VARCHAR(64)
)
BEGIN
    DECLARE old_index_name VARCHAR(64) DEFAULT NULL;

    SELECT candidate.INDEX_NAME
    INTO old_index_name
    FROM (
        SELECT
            INDEX_NAME,
            GROUP_CONCAT(
                COLUMN_NAME
                ORDER BY SEQ_IN_INDEX
                SEPARATOR ','
            ) AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND NON_UNIQUE = 0
          AND INDEX_NAME <> 'PRIMARY'
        GROUP BY INDEX_NAME
    ) candidate
    WHERE candidate.indexed_columns = 'exam_id,student_id'
    LIMIT 1;

    IF old_index_name IS NOT NULL THEN
        SET @drop_index_sql = CONCAT(
            'ALTER TABLE `',
            target_table,
            '` DROP INDEX `',
            old_index_name,
            '`'
        );
        PREPARE drop_index_statement FROM @drop_index_sql;
        EXECUTE drop_index_statement;
        DEALLOCATE PREPARE drop_index_statement;
    END IF;
END//

DELIMITER ;

CALL drop_old_exam_student_unique_index('exam_attempts');
CALL drop_old_exam_student_unique_index('quiz_submissions');
CALL drop_old_exam_student_unique_index('essay_submissions');

DROP PROCEDURE drop_old_exam_student_unique_index;
