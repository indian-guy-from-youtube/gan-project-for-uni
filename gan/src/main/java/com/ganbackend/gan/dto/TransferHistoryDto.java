package com.ganbackend.gan.dto;

import com.ganbackend.gan.entity.TransferHistory;
import lombok.Builder;
import lombok.Data;
 
import java.time.LocalDateTime;

 
// ─── История запросов ────────────────────────────────────────
@Data
@Builder
class TransferHistoryDtoImpl {
    private Long id;
    private String originalFilename;
    private String style;
    private int outputSize;
    private long resultSizeBytes;
    private long processingTimeMs;
    private boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}
 
public record TransferHistoryDto(
        Long id,
        String originalFilename,
        String style,
        int outputSize,
        long resultSizeBytes,
        long processingTimeMs,
        boolean success,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static TransferHistoryDto from(TransferHistory h) {
        return new TransferHistoryDto(
                h.getId(),
                h.getOriginalFilename(),
                h.getStyle(),
                h.getOutputSize(),
                h.getResultSizeBytes(),
                h.getProcessingTimeMs(),
                h.isSuccess(),
                h.getErrorMessage(),
                h.getCreatedAt()
        );
    }
}
 