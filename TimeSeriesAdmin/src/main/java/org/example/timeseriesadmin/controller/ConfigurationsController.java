package org.example.timeseriesadmin.controller;

import javafx.scene.control.ComboBox;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.controlsfx.control.CheckComboBox;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.timeseriesadmin.util.MetadataUtil;
import org.example.timeseriesadmin.controller.ConfigurationsListController.ConfigurationItem;
import javafx.scene.control.TableView;

import static org.example.timeseriesadmin.util.ConfigurationListUtil.notifyConfigurationListUpdate;


public class ConfigurationsController {

  @FXML
  private ComboBox<String> nameComboBox;
  @FXML
  private CheckComboBox<String> dataCheckComboBox;

  @FXML
  private TextField nbvField;
  @FXML
  private TextField zoomField;
  @FXML
  private Label responseLabelConfig;
  @FXML
  private ProgressBar progressBar;

  private TableView<ConfigurationItem> configTable;



  HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(60))
      .build();

  @FXML
  private void initialize() {
    progressBar.setVisible(false);
    dataCheckComboBox.getItems().addAll("min", "max", "median", "average", "quantile");
    // Check and wait for a valid IP address before loading metadata
    MetadataUtil.checkAndLoadMetadata(nameComboBox, responseLabelConfig);
  }

  @FXML
  private void handleSubmit() {
    String name = nameComboBox.getValue();
    if(name == null) {
      responseLabelConfig.setText("Please select a metadata name.");
      return;
    }

    List<String> data = dataCheckComboBox.getCheckModel().getCheckedItems();
    if(data.isEmpty()) {
      responseLabelConfig.setText("Please select at least one data aggregate.");
      return;
    }
    if(data == null) {
      responseLabelConfig.setText("Please select at least one data aggregate.");
      return;
    }


    int nbv;
    try {
      nbv = Integer.parseInt(nbvField.getText());
    } catch (NumberFormatException e) {
      responseLabelConfig.setText("Number of values (Nbv) must be an integer.");
      return;
    }

    Map<Integer, String> zoom = new HashMap<>();
    String[] zoomPairs = zoomField.getText().split(",");
    for (String pair : zoomPairs) {
      String[] keyValue = pair.split(":");
      try {
        zoom.put(Integer.parseInt(keyValue[0].trim()), keyValue[1].trim());
      } catch (NumberFormatException e) {
        responseLabelConfig.setText("Invalid zoom format. Expected format: key:value,key:value...");
        return;
      }
    }

    // Asynchronous task for making the HTTP request
    Task<Void> task = new Task<Void>() {
      @Override
      protected Void call() throws Exception {
        String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
        StringBuilder urlBuilder = new StringBuilder("http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/config-data/")
            .append(URLEncoder.encode(name, StandardCharsets.UTF_8));

        for (String datum : data) {
          urlBuilder.append("&data=").append(encodeValue(datum));
        }

        urlBuilder.append("&nbv=").append(nbv);

        String url = urlBuilder.toString().replaceFirst("&", "?");

        // Convert zoom map to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String zoomJson = objectMapper.writeValueAsString(zoom);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(new java.net.URI(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(zoomJson))
            .build();

        System.out.println("Request: " + request);
        System.out.println("Request body: " + zoomJson);
        System.out.println("Request URL: " + url);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Platform.runLater(() -> {
          progressBar.setVisible(false);
          if (response.statusCode() == 200) {
            notifyConfigurationListUpdate(configTable, responseLabelConfig); // Reload configurations after successful configuration
            responseLabelConfig.setText("Configuration successful.");
          } else {
            responseLabelConfig.setText("Configuration failed: " + response.body());
            System.out.println("Configuration failed: " + response.body());
          }
        });

        return null;
      }

      @Override
      protected void scheduled() {
        Platform.runLater(() -> progressBar.setVisible(true));
      }
    };

    new Thread(task).start();
  }

  private String encodeValue(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("%3A", ":");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
