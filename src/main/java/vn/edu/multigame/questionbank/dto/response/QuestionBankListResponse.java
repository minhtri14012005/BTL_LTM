package vn.edu.multigame.questionbank.dto.response;

import java.util.List;

public record QuestionBankListResponse(List<QuestionBankMetadataResponse> items, int page, int size, long totalElements) {}
