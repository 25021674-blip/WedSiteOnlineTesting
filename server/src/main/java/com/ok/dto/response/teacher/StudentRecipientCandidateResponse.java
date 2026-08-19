package com.ok.dto.response.teacher;

public record StudentRecipientCandidateResponse(
        Long studentId,
        String fullName,
        String email,
        boolean selected
) {
}
