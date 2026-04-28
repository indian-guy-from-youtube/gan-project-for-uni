package org.example.frontjavafx.service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

/**
 * Сервис для обращения к Spring backend.
 * Spring backend в свою очередь проксирует запрос на Python GAN-сервер.
 *
 * Endpoint: POST http://localhost:8080/api/style/transfer
 * Multipart form-data: image (file) + style (string) + size (int)
 * Ответ: бинарный PNG
 */
public class StyleTransferService {

    private static final String SPRING_BASE_URL = "http://localhost:8080/api/style";
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient httpClient;
    private String lastProcessingTime = "";

    public StyleTransferService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Отправляет изображение и стиль на Spring backend.
     * Возвращает байты PNG-результата.
     */
    public byte[] transferStyle(File imageFile, String style, int size) throws Exception {
        String boundary = "----Boundary" + UUID.randomUUID().toString().replace("-", "");

        byte[] body = buildMultipartBody(boundary, imageFile, style, size);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SPRING_BASE_URL + "/transfer"))
            .timeout(TIMEOUT)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(BodyPublishers.ofByteArray(body))
            .build();

        long start = System.currentTimeMillis();
        HttpResponse<byte[]> response = httpClient.send(request, BodyHandlers.ofByteArray());
        long elapsed = System.currentTimeMillis() - start;

        if (response.statusCode() != 200) {
            String errorBody = new String(response.body());
            throw new RuntimeException("Сервер вернул ошибку " + response.statusCode() + ": " + errorBody);
        }

        // Читаем время обработки из заголовка (если Spring его прокидывает)
        lastProcessingTime = response.headers()
            .firstValue("X-Processing-Time")
            .orElse(String.format("%.2fs", elapsed / 1000.0));

        return response.body();
    }

    public String getLastProcessingTime() {
        return lastProcessingTime;
    }

    /**
     * Формирует multipart/form-data тело запроса вручную.
     */
    private byte[] buildMultipartBody(String boundary, File imageFile, String style, int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String CRLF = "\r\n";
        String dashes = "--" + boundary;

        // Поле: image (файл)
        String mimeType = imageFile.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        writeLine(out, dashes);
        writeLine(out, "Content-Disposition: form-data; name=\"image\"; filename=\"" + imageFile.getName() + "\"");
        writeLine(out, "Content-Type: " + mimeType);
        writeLine(out, "");
        out.write(Files.readAllBytes(imageFile.toPath()));
        writeLine(out, "");

        // Поле: style
        writeLine(out, dashes);
        writeLine(out, "Content-Disposition: form-data; name=\"style\"");
        writeLine(out, "");
        writeLine(out, style);

        // Поле: size
        writeLine(out, dashes);
        writeLine(out, "Content-Disposition: form-data; name=\"size\"");
        writeLine(out, "");
        writeLine(out, String.valueOf(size));

        // Завершение
        writeLine(out, dashes + "--");

        return out.toByteArray();
    }

    private void writeLine(ByteArrayOutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes("UTF-8"));
    }
}
