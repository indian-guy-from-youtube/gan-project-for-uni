error id: file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java
file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[1,1]

error in qdox parser
file content:
```java
offset: 1
uri: file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java
text:
```scala
p@@ackage 

import com.styletransfer.backend.dto.StylesResponse;
import com.styletransfer.backend.dto.TransferHistoryDto;
import com.styletransfer.backend.service.StyleTransferService;
import lombok.RequiredArgsConstructor;
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

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1583)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/main/java/com/ganbackend/gan/controller/StyleTransferController.java