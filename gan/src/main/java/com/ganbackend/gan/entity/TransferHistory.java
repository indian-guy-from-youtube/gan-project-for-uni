package com.ganbackend.gan.entity;

import jakarta.persistence.*;
import lombok.*;
 
import java.time.LocalDateTime;
 
@Entity
@Table(name = "transfer_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferHistory {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @Column(name = "original_filename")
    private String originalFilename;
 
    @Column(name = "style", nullable = false)
    private String style;
 
    @Column(name = "output_size")
    private int outputSize;
 
    @Column(name = "result_size_bytes")
    private long resultSizeBytes;
 
    @Column(name = "processing_time_ms")
    private long processingTimeMs;
 
    @Column(name = "success")
    private boolean success;
 
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
 
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
 