package org.example.frontjavafx.app;

import org.example.frontjavafx.model.StyleOption;
import org.example.frontjavafx.service.StyleTransferService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.geometry.Insets;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // ─── FXML-поля ───────────────────────────────────────────────
    @FXML private ImageView originalImageView;
    @FXML private ImageView resultImageView;
    @FXML private Label originalPlaceholder;
    @FXML private Label resultPlaceholder;

    @FXML private FlowPane stylesPane;
    @FXML private Slider sizeSlider;
    @FXML private Label sizeLabel;

    @FXML private Button uploadButton;
    @FXML private Button transferButton;
    @FXML private Button saveButton;
    @FXML private Button swapButton;

    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private Label processingTimeLabel;

    // ─── State ───────────────────────────────────────────────────
    private final StyleTransferService service = new StyleTransferService();
    private File selectedFile;
    private byte[] resultImageBytes;
    private String selectedStyle = "monet";

    private static final List<StyleOption> STYLES = List.of(
        new StyleOption("monet",   "Моне",    "Импрессионизм",   "#7EB8D4"),
        new StyleOption("vangogh", "Ван Гог", "Экспрессионизм",  "#E8A87C"),
        new StyleOption("ukiyoe",  "Укиё-э",  "Японская гравюра","#9EC8B0"),
        new StyleOption("cezanne", "Сезанн",  "Постимпрессионизм","#C4A8D8")
    );

    // ─── Инициализация ───────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buildStyleCards();
        setupSizeSlider();
        transferButton.setDisable(true);
        saveButton.setDisable(true);
        swapButton.setDisable(true);
        progressIndicator.setVisible(false);
        setStatus("Загрузите изображение для начала работы");
    }

    private void buildStyleCards() {
        stylesPane.getChildren().clear();
        for (StyleOption style : STYLES) {
            VBox card = createStyleCard(style);
            stylesPane.getChildren().add(card);
        }
    }

    private VBox createStyleCard(StyleOption style) {
        VBox card = new VBox(4);
        card.getStyleClass().add("style-card");
        card.setPrefWidth(115);
        card.setPadding(new Insets(10, 12, 10, 12));

        // Цветовой индикатор
        Region colorDot = new Region();
        colorDot.setPrefSize(28, 28);
        colorDot.setStyle("-fx-background-color: " + style.color() + ";" +
                          "-fx-background-radius: 14;");

        Label nameLabel = new Label(style.displayName());
        nameLabel.getStyleClass().add("style-card-name");

        Label descLabel = new Label(style.description());
        descLabel.getStyleClass().add("style-card-desc");
        descLabel.setWrapText(true);

        card.getChildren().addAll(colorDot, nameLabel, descLabel);

        if (style.id().equals(selectedStyle)) {
            card.getStyleClass().add("style-card-selected");
        }

        card.setOnMouseClicked(e -> selectStyle(style.id(), card));
        return card;
    }

    private void selectStyle(String styleId, VBox clickedCard) {
        selectedStyle = styleId;
        // Снимаем выделение со всех карточек
        stylesPane.getChildren().forEach(node ->
            node.getStyleClass().remove("style-card-selected")
        );
        clickedCard.getStyleClass().add("style-card-selected");
    }

    private void setupSizeSlider() {
        sizeSlider.setMin(256);
        sizeSlider.setMax(1024);
        sizeSlider.setValue(512);
        sizeSlider.setMajorTickUnit(256);
        sizeSlider.setSnapToTicks(true);
        sizeLabel.setText("512 px");
        sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int snapped = (int)(Math.round(newVal.doubleValue() / 128.0) * 128);
            sizeLabel.setText(snapped + " px");
        });
    }

    // ─── Загрузка изображения ─────────────────────────────────────
    @FXML
    private void onUploadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите изображение");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"),
            new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );
        File file = chooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (file != null) {
            selectedFile = file;
            Image img = new Image(file.toURI().toString());
            originalImageView.setImage(img);
            originalPlaceholder.setVisible(false);

            // Сброс результата
            resultImageView.setImage(null);
            resultPlaceholder.setVisible(true);
            resultImageBytes = null;
            saveButton.setDisable(true);
            swapButton.setDisable(true);

            transferButton.setDisable(false);
            setStatus("Изображение загружено: " + file.getName());
            processingTimeLabel.setText("");
        }
    }

    // ─── Перенос стиля ────────────────────────────────────────────
    @FXML
    private void onTransferStyle() {
        if (selectedFile == null) return;

        int size = (int)(Math.round(sizeSlider.getValue() / 128.0) * 128);

        setProcessing(true);
        setStatus("Применяю стиль «" + getStyleDisplayName(selectedStyle) + "»...");
        processingTimeLabel.setText("");

        Task<byte[]> task = new Task<>() {
            @Override
            protected byte[] call() throws Exception {
                return service.transferStyle(selectedFile, selectedStyle, size);
            }
        };

        task.setOnSucceeded(e -> {
            resultImageBytes = task.getValue();
            Image resultImg = new Image(new ByteArrayInputStream(resultImageBytes));
            resultImageView.setImage(resultImg);
            resultPlaceholder.setVisible(false);

            String time = service.getLastProcessingTime();
            processingTimeLabel.setText("⏱ " + time);
            setStatus("Готово! Стиль «" + getStyleDisplayName(selectedStyle) + "» применён успешно.");
            saveButton.setDisable(false);
            swapButton.setDisable(false);
            setProcessing(false);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            setStatus("Ошибка: " + ex.getMessage());
            showError("Ошибка обработки", ex.getMessage());
            setProcessing(false);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    // ─── Сохранение результата ────────────────────────────────────
    @FXML
    private void onSaveResult() {
        if (resultImageBytes == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить результат");
        chooser.setInitialFileName("styled_" + selectedStyle + ".png");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PNG изображение", "*.png")
        );
        File file = chooser.showSaveDialog(saveButton.getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.write(file.toPath(), resultImageBytes);
                setStatus("Сохранено: " + file.getName());
            } catch (Exception ex) {
                showError("Ошибка сохранения", ex.getMessage());
            }
        }
    }

    // ─── Поменять местами ─────────────────────────────────────────
    @FXML
    private void onSwapImages() {
        if (resultImageBytes == null) return;

        // Сохраняем результат как новый входной файл во временную папку
        try {
            File temp = File.createTempFile("swap_", ".png");
            temp.deleteOnExit();
            java.nio.file.Files.write(temp.toPath(), resultImageBytes);
            selectedFile = temp;

            Image img = new Image(new ByteArrayInputStream(resultImageBytes));
            originalImageView.setImage(img);
            originalPlaceholder.setVisible(false);

            resultImageView.setImage(null);
            resultPlaceholder.setVisible(true);
            resultImageBytes = null;
            saveButton.setDisable(true);
            swapButton.setDisable(true);

            setStatus("Результат стал исходным изображением. Выберите новый стиль.");
            processingTimeLabel.setText("");
        } catch (Exception ex) {
            showError("Ошибка", ex.getMessage());
        }
    }

    // ─── Утилиты ──────────────────────────────────────────────────
    private void setProcessing(boolean processing) {
        Platform.runLater(() -> {
            progressIndicator.setVisible(processing);
            transferButton.setDisable(processing);
            uploadButton.setDisable(processing);
        });
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message != null ? message : "Неизвестная ошибка");
            alert.showAndWait();
        });
    }

    private String getStyleDisplayName(String styleId) {
        return STYLES.stream()
            .filter(s -> s.id().equals(styleId))
            .map(StyleOption::displayName)
            .findFirst()
            .orElse(styleId);
    }
}
