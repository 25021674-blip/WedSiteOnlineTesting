# API bài kiểm tra - bản đồ đọc code

## 1. Bài toán tổng quát

Hệ thống có hai loại bài kiểm tra:

- `MULTIPLE_CHOICE`: học sinh chọn đáp án, server tự chấm.
- `ESSAY`: học sinh tải một file PDF lên trước hạn, giáo viên tải xuống và chấm.

Mọi bài kiểm tra đều đi qua `ExamEntity`. Sau đó luồng rẽ sang nhóm `multiple` hoặc `essay`.

## 2. Cách chọn file để xây dựng một API

Luôn suy nghĩ theo thứ tự:

1. Xác định dữ liệu cần lưu -> `Entity` và enum.
2. Xác định cách truy vấn dữ liệu -> `Repository`.
3. Xác định JSON vào/ra -> request/response trong `dto`.
4. Viết quy tắc nghiệp vụ -> `Service`.
5. Mở URL HTTP cho client -> `Controller`.
6. Chặn truy cập sai -> security và kiểm tra role/ownership trong service.
7. Viết test cho trường hợp đúng và sai.

Khi đọc một request đang chạy, đọc theo chiều ngược lại:

`Controller -> DTO request -> Service -> Repository -> Entity -> DTO response`.

## 3. Thứ tự đọc toàn bộ code

### Nền tảng

1. `dto/Role.java`
2. `dto/ExamType.java`
3. `dto/ExamStatus.java`
4. `entity/UserEntity.java`
5. `entity/ExamEntity.java`

### API quản lý đề

6. `dto/CreateExamRequest.java`
7. `dto/UpdateExamRequest.java`
8. `dto/UpdateExamStatusRequest.java`
9. `dto/ExamResponse.java`
10. `repository/ExamRepository.java`
11. `exam/service/ExamService.java`
12. `exam/controller/ExamController.java`

### Nhánh tự luận

13. `essay/entity/EssayAssignmentFileEntity.java`
14. `repository/EssayAssignmentFileRepository.java`
15. `dto/EssayAssignmentFileResponse.java`
16. `essay/service/FileStorageService.java`
17. `essay/service/EssayAssignmentFileService.java`
18. `essay/controller/EssayAssignmentFileController.java`
19. `essay/entity/EssaySubmissionEntity.java`
20. `repository/EssaySubmissionRepository.java`
21. `dto/EssaySubmissionResponse.java`
22. `dto/GradeEssayRequest.java`
23. `essay/service/EssaySubmissionService.java`
24. `essay/controller/EssaySubmissionController.java`

### Nhánh trắc nghiệm

20. `quiz/entity/QuestionEntity.java`
21. `quiz/entity/AnswerOptionEntity.java`
22. `quiz/entity/QuizSubmissionEntity.java`
23. `quiz/entity/QuizSubmissionAnswerEntity.java`
24. Ba repository tương ứng trong `repository/` (lựa chọn được lưu qua cascade của câu hỏi).
25. `dto/AnswerOptionRequest.java`
26. `dto/CreateQuestionRequest.java`
27. Các response có chữ `Student` và `Management`.
28. `quiz/service/QuestionService.java`
29. `quiz/controller/QuestionController.java`
30. `dto/SelectedAnswerRequest.java`
31. `dto/SubmitQuizRequest.java`
32. `dto/QuizResultResponse.java`
33. `quiz/service/QuizSubmissionService.java`
34. `quiz/controller/QuizSubmissionController.java`

### Xác thực, lỗi và cấu hình

35. `auth/security/JwtAuthenticationFilter.java`
36. `auth/security/SecurityConfig.java`
37. `auth/exceptionHandle/AuthExceptionHandler.java`
38. `resources/application.properties`
39. Các test trong `src/test/java`.

## 4. Các URL chính

- Quản lý đề: `/api/exams`
- Quản lý câu hỏi: `/api/exams/{examId}/questions`
- Học sinh xem đề trắc nghiệm: `/api/exams/{examId}/quiz`
- Nộp trắc nghiệm: `/api/exams/{examId}/quiz-submissions`
- Nộp PDF: `/api/exams/{examId}/essay-submissions`
- Tải PDF: `/api/essay-submissions/{submissionId}/file`
- Chấm tự luận: `/api/essay-submissions/{submissionId}/grade`

## 5. Lưu ý khi chạy thử

API đăng ký hiện tạo tài khoản `STUDENT`. Muốn thử chức năng giáo viên trong môi trường local,
đổi role của một tài khoản đã đăng ký trong MySQL:

```sql
UPDATE users SET role = 'TEACHER' WHERE email = 'teacher@example.com';
```

Đăng nhập lại sau khi đổi role để nhận JWT mới. Tạo đề xong phải đổi trạng thái sang
`PUBLISHED` thì học sinh mới xem hoặc nộp được.
