package vn.edu.quiz.quiz.dto.response;

import java.util.List;

public record QuizListResponse(List<QuizMetadataResponse> items, int page, int size, long totalElements) {}
