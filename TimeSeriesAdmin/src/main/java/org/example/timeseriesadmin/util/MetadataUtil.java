package org.example.timeseriesadmin.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class MetadataUtil {

  private static final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(60))
      .build();

  public static void loadMetadata(ComboBox<String> comboBox, Label responseLabel) {
    String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
    if (ipAddress == null || ipAddress.isEmpty()) {
      responseLabel.setText("IP Address is not set. Please set it in the Settings tab.");
      return;
    }

    String url = "http://" + ipAddress + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-all-metadataname";
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) {
          responseLabel.setText("No metadata found.");
          return;
        }
        ObservableList<String> metadataList = FXCollections.observableArrayList(responseBody.split("\n"));
        comboBox.setItems(metadataList);
        responseLabel.setText("Metadata loaded successfully.");
      } else {
        responseLabel.setText("Failed to load metadata: " + response.statusCode() + " - " + response.body());
        System.out.println("Failed to load metadata: " + response.statusCode() + " - " + response.body());
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
    }
  }

  public  static void checkAndLoadMetadata(ComboBox comboBox, Label responseLabel) {
    javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
      @Override
      protected Void call() throws Exception {
        while (!checkIpAddress(responseLabel)) {
          Thread.sleep(1000); // wait for 1 second before checking again
        }
        javafx.application.Platform.runLater(() -> MetadataUtil.loadMetadata(comboBox, responseLabel));
        return null;
      }
    };
    new Thread(task).start();
  }

  public static boolean checkIpAddress(Label responseLabel) {
    String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
    if (ipAddress == null || ipAddress.isEmpty()) {
      responseLabel.setText("IP Address is not set. Please set it in the Settings tab.");
      return false;
    }
    return true;
  }


}
