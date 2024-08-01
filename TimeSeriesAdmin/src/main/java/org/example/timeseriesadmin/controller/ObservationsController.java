package org.example.timeseriesadmin.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import com.opencsv.CSVReader;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javafx.application.Platform;
import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import java.net.http.HttpRequest.BodyPublisher;
import org.example.timeseriesadmin.util.MetadataUtil;

public class ObservationsController {

  @FXML
  private Label responseLabelObservations;
  @FXML
  private ProgressBar progressBar;
  @FXML
  private Label progressLabel;
  @FXML
  private ComboBox<String> metadataComboBox;

  private File selectedFile;
  HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(60))
      .build();
  private MainViewController mainController;

  public void setMainController(MainViewController mainController) {
    this.mainController = mainController;
  }

  @FXML
  private void initialize() {
    // Check and wait for a valid IP address before loading metadata
    MetadataUtil.checkAndLoadMetadata(metadataComboBox, responseLabelObservations);
  }

  private HttpResponse<String> makeGetRequest(String url) throws java.net.URISyntaxException, java.io.IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new java.net.URI(url))
        .GET()
        .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }


  @FXML
  private void handleChooseFile() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
        new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
    );

    selectedFile = fileChooser.showOpenDialog(new Stage());
    this.initialize();
    if (selectedFile != null) {
      responseLabelObservations.setText("File selected: " + selectedFile.getName());
    }
  }

  @FXML
  private void handleSend() {
    if (selectedFile == null) {
      responseLabelObservations.setText("Please choose a file first.");
      return;
    }

    if (metadataComboBox.getValue() == null) {
      responseLabelObservations.setText("Please select a metadata.");
      return;
    }

    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        updateProgress(0, 1);
        updateMessage("Converting file to JSON...");
        File jsonFile = convertFileToJson(selectedFile);

        updateProgress(0.5, 1);
        updateMessage("Uploading file to server...");
        sendFileToServer(jsonFile, response -> {
          if (response.statusCode() == 200) {
            Platform.runLater(() -> {
              updateProgress(1, 1);
              updateMessage("File uploaded and associated with metadata.");
            });
          } else {
            Platform.runLater(() -> {
              updateProgress(1, 1);
              updateMessage("Failed to upload file: " + response.body());
              System.out.println("Failed to upload file: " + response.body());
            });
          }
        });
        return null;
      }

      private File convertFileToJson(File file) throws IOException {
        if (file.getName().endsWith(".csv")) {
          return convertCsvToJson(file);
        } else if (file.getName().endsWith(".xlsx")) {
          return convertExcelToJson(file);
        }
        throw new IllegalArgumentException("Unsupported file type");
      }

      private File convertCsvToJson(File csvFile) throws IOException {
        File jsonFile = new File(csvFile.getAbsolutePath().replace(".csv", ".json"));
        try (CSVReader reader = new CSVReader(new FileReader(csvFile));
             OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8);
             JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {

          jsonGenerator.writeStartArray();
          String[] headers = reader.readNext(); // Lire les en-têtes
          String[] row;
          int rowCount = 0;
          while ((row = reader.readNext()) != null) {
            jsonGenerator.writeStartObject();
            for (int i = 0; i < headers.length; i++) {
              jsonGenerator.writeStringField(headers[i], row[i]);
            }
            jsonGenerator.writeEndObject();
            rowCount++;
            if (rowCount % 100 == 0) { // update progress every 100 rows
              updateProgress((double) rowCount / 2000, 1); // assuming a rough estimate of 2000 rows
            }
          }
          jsonGenerator.writeEndArray();
        } catch (com.opencsv.exceptions.CsvException e) {
          e.printStackTrace();
          throw new IOException("Error processing CSV file", e);
        }
        return jsonFile;
      }

      private File convertExcelToJson(File excelFile) throws IOException {
        File jsonFile = new File(excelFile.getAbsolutePath().replace(".xlsx", ".json"));
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis);
             OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8);
             JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {

          Sheet sheet = workbook.getSheetAt(0);
          Iterator<Row> rowIterator = sheet.iterator();
          jsonGenerator.writeStartArray();
          Row headerRow = rowIterator.next();
          List<String> headers = new ArrayList<>();
          headerRow.forEach(cell -> headers.add(cell.getStringCellValue()));
          int rowCount = 0;
          while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            jsonGenerator.writeStartObject();
            for (int i = 0; i < headers.size(); i++) {
              Cell cell = row.getCell(i);
              jsonGenerator.writeStringField(headers.get(i), cell.toString());
            }
            jsonGenerator.writeEndObject();
            rowCount++;
            if (rowCount % 100 == 0) { // update progress every 100 rows
              updateProgress((double) rowCount / 2000, 1); // assuming a rough estimate of 2000 rows
            }
          }
          jsonGenerator.writeEndArray();
        }
        return jsonFile;
      }

      private void sendFileToServer(File file, java.util.function.Consumer<HttpResponse<String>> callback) throws IOException, java.net.URISyntaxException {
        String metadata = metadataComboBox.getValue();
        if (metadata.isEmpty()) {
          Platform.runLater(() -> responseLabelObservations.setText("Metadata selection is required."));
          return;
        }
        //pemite to send data by chunks
        BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofFile(file.toPath());

        String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI("http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/add-observations?name=" + metadata))
            .timeout(Duration.ofMinutes(5))
            .header("Content-Type", "application/json")
            .POST(bodyPublisher)
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(callback)
            .exceptionally(e -> {
              e.printStackTrace();
              Platform.runLater(() -> responseLabelObservations.setText("Error: " + e.getMessage()));
              return null;
            });
      }
    };

    progressBar.progressProperty().bind(task.progressProperty());
    progressLabel.textProperty().bind(task.messageProperty());

    Thread thread = new Thread(task);
    thread.setDaemon(true);
    thread.start();
  }
}
