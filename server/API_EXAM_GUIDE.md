# API bài kiểm tra - bản đồ đọc code

## Kiến trúc hiện tại

Hệ thống có hai luồng nghiệp vụ:

- `MULTIPLE_CHOICE`: làm bài trực tuyến, lưu từng đáp án và tự chấm.
- `ESSAY`: tải đề PDF, học sinh nộp PDF và giáo viên chấm thủ công.

Mọi đề thi dùng `ExamEntity`. Lượt làm bài trực tuyến chỉ có một nguồn dữ liệu:

```text
ExamAttemptEntity
  -> StudentAnswerEntity
  -> AttemptViolationEntity
```

Các entity `QuizSubmissionEntity` và `QuizSubmissionAnswerEntity` cũ đã được loại bỏ.

## Thứ tự đọc code

### Nền tảng

1. `domain/enums/`
2. `entity/UserEntity.java`
3. `entity/ExamEntity.java`
4. `auth/security/`

### Quản lý đề và câu hỏi

1. `dto/request/teacher/`
2. `exam/service/ExamService.java`
3. `exam/controller/ExamController.java`
4. `exam/quiz/service/QuestionService.java`
5. `exam/quiz/controller/QuestionController.java`

### Học sinh làm bài trực tuyến

1. `entity/ExamAttemptEntity.java`
2. `entity/StudentAnswerEntity.java`
3. `dto/request/student/`
4. `exam/student/service/StudentExamAttemptService.java`
5. `exam/student/service/StudentAnswerSaveService.java`
6. `exam/student/service/ExamAttemptCompletionService.java`
7. `exam/student/controller/`
8. `exam/student/scheduler/ExamAttemptAutoSubmitScheduler.java`

### Tự luận PDF

1. `entity/EssayAssignmentFileEntity.java`
2. `entity/EssaySubmissionEntity.java`
3. `exam/essay/service/`
4. `exam/essay/controller/`

### Database

Flyway quản lý schema trong `resources/db/migration/`. Hibernate chạy với
`ddl-auto=validate`, chỉ đối chiếu entity và schema chứ không tự sửa database.

## API chính

- Quản lý đề: `/api/exams`
- Quản lý câu hỏi: `/api/exams/{examId}/questions`
- Bắt đầu/tiếp tục: `POST /api/student/exams/{examId}/attempts/start`
- Lưu một đáp án: `PUT /api/student/exam-attempts/{attemptId}/questions/{questionId}/answer`
- Nộp bài: `POST /api/student/exams/{examId}/attempts/{attemptId}/submit`
- Kết quả của học sinh: `GET /api/student/exams/{examId}/attempts/me`
- Nộp PDF: `/api/exams/{examId}/essay-submissions`
- Giáo viên xem bài: `/api/teacher/exams/{examId}/submissions`

## Quy tắc thời lượng

- Đề trực tuyến phải có `durationMinutes > 0`.
- Đề `ESSAY` phải có `durationMinutes = null` và dùng deadline chung.
- Database cho phép `duration_minutes` null; `ExamService` chịu trách nhiệm kiểm tra theo loại đề.

## Test

Xem `TESTING.md`. Integration test H2 chạy nhanh; script MySQL tạo lại riêng
`online_testing_test`, chạy Flyway và xác minh cùng suite trên MySQL 8.
