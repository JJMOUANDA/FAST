package org.example.timeseriesadmin.util;

import  org.example.timeseriesadmin.controller.ConfigurationsListController.ConfigurationItem;


import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javafx.scene.control.TableView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import java.util.ArrayList;
public class ConfigurationListUtil {

  private static final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(60))
      .build();
  public static void loadConfigurations(TableView<ConfigurationItem> configTable, Label responseLabel) {
    String ipAddress = IpAddressManager.getInstance().getIpAddress();
    if (ipAddress == null || ipAddress.isEmpty()) {
      System.out.println("IP Address is not set. Please set it in the Settings tab.");
      return;
    }

    String url = "http://" + ipAddress + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-configurations";
    try {
      HttpRequest request = java.net.http.HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String responseBody = response.body();
        String[] lines = responseBody.split("\n");

        java.util.List<org.example.timeseriesadmin.controller.ConfigurationsListController.ConfigurationItem> configurations = new ArrayList<>();
        System.out.println("lines: " + responseBody);
        for (String line : lines) {
          ConfigurationItem item = new ConfigurationItem();
          item.setName(line.trim()); // assuming ConfigurationItem has a setName method
          configurations.add(item);
        }

        ObservableList<ConfigurationItem> data = FXCollections.observableArrayList(configurations);
        configTable.setItems(data);
      } else {
        responseLabel.setText("Failed to load configurations" );
        System.out.println("Failed to load configurations: " + response.statusCode() + " - " + response.body());
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
    }
  }


  public static void checkAndLoadConfigurations(TableView<ConfigurationItem> configTable, Label responseLabel) {
    javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
      @Override
      protected Void call() throws Exception {
        while (!checkIpAddress(responseLabel)) {
          Thread.sleep(1000); // wait for 1 second before checking again
        }
        javafx.application.Platform.runLater(() -> loadConfigurations(configTable, responseLabel));
        return null;
      }
    };
    new Thread(task).start();
  }

  public static boolean checkIpAddress(javafx.scene.control.Label responseLabel) {
    String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
    if (ipAddress == null || ipAddress.isEmpty()) {
      //System.out.println("IP Address is not set. Please set it in the Settings tab.");
      return false;
    }
    return true;
  }

  public static void notifyConfigurationListUpdate(TableView<ConfigurationItem> configTable, Label responseLabel) {
    checkAndLoadConfigurations(configTable, responseLabel);
  }
}
