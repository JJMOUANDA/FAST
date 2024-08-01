package org.example.timeseriesadmin.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import java.net.http.HttpClient;


public class SettingsIPController {

  @FXML
  private TextField ipAddressField;

  @FXML
  private Label responseLabel;

  private MainViewController mainController;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  public void setMainController(MainViewController mainController) {
    this.mainController = mainController;
  }

  @javafx.fxml.FXML
  private void handleSave() {
    String ipAddress = ipAddressField.getText();
    ipAddress.replaceAll(" ", "");
    if (ipAddress.isEmpty()) {
      responseLabel.setText("IP Address cannot be empty.");
      return;
    }
    if (!isValidIpAddress(ipAddress)) {
      responseLabel.setText("Invalid IP Address format. Expected format: x.x.x.x (space warning!)");
      return;
    }
    System.out.println("IP Address: " + ipAddress);
    org.example.timeseriesadmin.util.IpAddressManager.getInstance().setIpAddress(ipAddress);
    System.out.println(org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress());
    responseLabel.setText("IP Address saved successfully.");

    /* objectif de ce code etait de  verifier si le serveur etait activé ou pas.
    String url = "http://" + ipAddress + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/test";
    HttpResponse<String> response;
    try {
      HttpRequest request = java.net.http.HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .GET()
          .build();

      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response==null){
        responseLabel.setText("Error: The server seems unavailable. Please check the IP address or the server and try again.");
        break;
      }
      if (response.statusCode() == 200) {
        responseLabel.setText("Connection successful.");
      } else {
        responseLabel.setText("Connection failed: " + response.body());
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
    }
    */
    // Notify MainViewController to check and enable tabs
    if (mainController != null) {
      mainController.checkAndEnableTabs();
      mainController.disableSettingsTab();
    }
  }

  private boolean isValidIpAddress(String ipAddress) {
    String ipPattern = "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$";
    return ipAddress.matches(ipPattern);
  }

}
