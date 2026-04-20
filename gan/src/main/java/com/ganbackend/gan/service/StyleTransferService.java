package com.ganbackend.gan.service;

import com.ganbackend.gan.dto.StylesResponse;
import com.ganbackend.gan.dto.TransferHistoryDto;
import com.ganbackend.gan.entity.TransferHistory;
import com.ganbackend.gan.repository.TransferHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
 
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
 
@Slf4j
@Service
public class StyleTransferService {
 
    @Value("${python.server.url:http://localhost:5000}")
    private String pythonServerUrl;
 
    private final RestTemplate restTemplate;
    private final TransferHistoryRepository historyRepository;
 
    public StyleTransferService(TransferHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
        this.restTemplate = new RestTemplate();
        // Увеличиваем таймаут — GAN может обрабатывать долго
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        this.restTemplate.setRequestFactory(factory);
    }
 
    /**
     * Проксирует запрос на Python GAN-сервер.
     * Сохраняет запись в историю.
     */
    public byte[] transferStyle(MultipartFile image, String style, int size) {
        long startMs = System.currentTimeMillis();
 
        try {
            // Формируем multipart запрос к Python
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
 
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
 
            // Оборачиваем файл в ByteArrayResource с именем
            ByteArrayResource fileResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() != null
                            ? image.getOriginalFilename() : "image.jpg";
                }
            };
 
            body.add("image", fileResource);
            body.add("style", style);
            body.add("size", String.valueOf(size));
 
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
 
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    pythonServerUrl + "/transfer",
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );
 
            byte[] resultBytes = response.getBody();
            long elapsedMs = System.currentTimeMillis() - startMs;
 
            // Сохраняем в историю
            saveToHistory(image.getOriginalFilename(), style, size,
                    resultBytes != null ? resultBytes.length : 0, elapsedMs, true, null);
 
            log.info("Transfer complete: style={}, elapsed={}ms, result={} bytes",
                    style, elapsedMs, resultBytes != null ? resultBytes.length : 0);
 
            return resultBytes;
 
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startMs;
            saveToHistory(image.getOriginalFilename(), style, size, 0, elapsedMs, false, e.getMessage());
            log.error("Transfer failed: {}", e.getMessage());
            throw new RuntimeException("Ошибка обращения к GAN-серверу: " + e.getMessage(), e);
        }
    }
 
    /**
     * Получает список стилей с Python-сервера.
     */
    public StylesResponse getAvailableStyles() {
        try {
            return restTemplate.getForObject(pythonServerUrl + "/styles", StylesResponse.class);
        } catch (Exception e) {
            log.warn("Не удалось получить стили с Python: {}", e.getMessage());
            // Возвращаем дефолтный список
            return StylesResponse.defaults();
        }
    }
 
    /**
     * Проверяет доступность Python-сервера.
     */
    public boolean isPythonServerAlive() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    pythonServerUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Python server недоступен: {}", e.getMessage());
            return false;
        }
    }
 
    public List<TransferHistoryDto> getHistory(int limit) {
        return historyRepository.findTopN(limit).stream()
                .map(TransferHistoryDto::from)
                .collect(Collectors.toList());
    }
 
    public void deleteHistoryRecord(Long id) {
        historyRepository.deleteById(id);
    }
 
    private void saveToHistory(String filename, String style, int size,
                               long resultBytes, long elapsedMs,
                               boolean success, String errorMessage) {
        try {
            TransferHistory record = TransferHistory.builder()
                    .originalFilename(filename)
                    .style(style)
                    .outputSize(size)
                    .resultSizeBytes(resultBytes)
                    .processingTimeMs(elapsedMs)
                    .success(success)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            historyRepository.save(record);
        } catch (Exception e) {
            log.warn("Не удалось сохранить историю: {}", e.getMessage());
        }
    }
}
 