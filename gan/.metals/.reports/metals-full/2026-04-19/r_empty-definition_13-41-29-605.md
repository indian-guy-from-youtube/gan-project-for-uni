error id: file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java:lombok/RequiredArgsConstructor#
file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java
empty definition using pc, found symbol in pc: lombok/RequiredArgsConstructor#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 242
uri: file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java
text:
```scala
package com.ganbackend.gan.controller;

import com.ganbackend.gan.backend.dto.StylesResponse;
import com.styletransfer.backend.dto.TransferHistoryDto;
import com.styletransfer.backend.service.StyleTransferService;
import lombok.RequiredArgsCo@@nstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
 
import java.util.List;
 
@Slf4j
@RestController
@RequestMapping("/api/style")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // разрешаем запросы от JavaFX
public class StyleTransferController {
 
    private final StyleTransferService styleTransferService;
 
    /**
     * GET /api/style/styles
     * Возвращает список доступных стилей с описанием.
     */
    @GetMapping("/styles")
    public ResponseEntity<StylesResponse> getStyles() {
        return ResponseEntity.ok(styleTransferService.getAvailableStyles());
    }
 
    /**
     * GET /api/style/health
     * Проверяет доступность Python GAN-сервера.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        boolean pythonAlive = styleTransferService.isPythonServerAlive();
        if (pythonAlive) {
            return ResponseEntity.ok("{\"status\":\"ok\",\"python\":\"up\"}");
        } else {
            return ResponseEntity.status(503).body("{\"status\":\"error\",\"python\":\"down\"}");
        }
    }
 
    /**
     * POST /api/style/transfer
     * Принимает изображение + стиль, проксирует на Python, возвращает PNG.
     *
     * @param image  загружаемый файл
     * @param style  название стиля (monet, vangogh, ukiyoe, cezanne)
     * @param size   размер выходного изображения (128-1024)
     */
    @PostMapping(value = "/transfer", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> transferStyle(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "style", defaultValue = "monet") String style,
            @RequestParam(value = "size", defaultValue = "512") int size
    ) {
        log.info("Transfer request: style={}, size={}, file={} ({} bytes)",
                style, size, image.getOriginalFilename(), image.getSize());
 
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
 
        byte[] result = styleTransferService.transferStyle(image, style, size);
 
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("X-Style", style)
                .body(result);
    }
 
    /**
     * GET /api/style/history
     * История всех запросов (из БД H2).
     */
    @GetMapping("/history")
    public ResponseEntity<List<TransferHistoryDto>> getHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(styleTransferService.getHistory(limit));
    }
 
    /**
     * DELETE /api/style/history/{id}
     * Удалить запись из истории.
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistory(@PathVariable Long id) {
        styleTransferService.deleteHistoryRecord(id);
        return ResponseEntity.noContent().build();
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: lombok/RequiredArgsConstructor#