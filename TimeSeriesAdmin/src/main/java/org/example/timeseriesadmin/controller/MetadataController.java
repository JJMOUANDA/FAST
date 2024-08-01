package org.example.timeseriesadmin.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.example.timeseriesadmin.util.MetadataUtil;
import javafx.scene.control.ComboBox;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;


public class MetadataController {

  @FXML
  private TextField name;
  @FXML
  private TextField unit;
  @FXML
  private TextField period;
  @FXML
  private TextField qmin;
  @FXML
  private TextField qmax;
  @FXML
  private Label responseLabel;


  private final HttpClient httpClient = HttpClient.newHttpClient();

  @FXML
  private void initialize() {
    addIntegerValidation(period);
    addIntegerValidation(qmin);
    addIntegerValidation(qmax);
  }

  private void addIntegerValidation(TextField textField) {
    textField.textProperty().addListener((observable, oldValue, newValue) -> {
      if (!newValue.matches("\\d*")) {
        textField.setText(newValue.replaceAll("[^\\d]", ""));
        responseLabel.setText("Please enter an integer value for " + textField.getId());
      }
    });
  }

  private String buildUrl(String name) {
    String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
    return String.format("http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-metadata/%s",
        URLEncoder.encode(name, StandardCharsets.UTF_8));
  }

  @FXML
  private boolean metadataExists(String name) {
    try {
      String url = buildUrl(name);
      HttpResponse<String> response = makeGetRequest(url);
      System.out.println("Metadata exists check response code: " + response.statusCode());
      System.out.println("Metadata exists check response body: " + response.body());
      if(response.statusCode() == 200) {
        if (response.body().equals("null") || response.body().isEmpty()){
          return false;
        }
        return true;
      }else {
        return false;
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
      return false;
    }
  }

  @FXML
  private boolean dataExists(String name) {
    try {
      if (metadataExists(name)) {
        String url = buildUrl(name);
        HttpResponse<String> response = makeGetRequest(url);
        if(response.body().contains("ERROR:")){
          return false;
        }
        if (response.statusCode() == 200) {
          String responseBody = response.body();
          for (String line : responseBody.split("\n")) {
            if (line.startsWith("start_date") || line.startsWith("end_date")) {
              String date = line.split(":")[1].trim();
              System.out.println("Date found: " + date);
              if (date.equals("null") || date.isEmpty()){
                return false;
              }
              return true;
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
    }
    return false;
  }

  private HttpResponse<String> makePostRequest(String url, String form) throws java.net.URISyntaxException, java.io.IOException, InterruptedException {
    HttpRequest request;
    if(form == null) {
      request = java.net.http.HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.noBody())
          .build();
    }else {
      request = java.net.http.HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(form))
          .build();
    }

    return httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> makeGetRequest(String url) throws java.net.URISyntaxException, java.io.IOException, InterruptedException {
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(new java.net.URI(url))
        .GET()
        .build();

    return httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  @FXML
  private void handleAdd() {
    if (name.getText().isEmpty() || unit.getText().isEmpty() || period.getText().isEmpty() || qmin.getText().isEmpty() || qmax.getText().isEmpty()) {
      responseLabel.setText("Please fill in all fields");
      return;
    }

    try {
      String nameString = name.getText();
      String unitString = unit.getText();
      String periodString = period.getText();
      String qminString = qmin.getText();
      String qmaxString = qmax.getText();

      int periodValue = Integer.parseInt(periodString);
      int qminValue = Integer.parseInt(qminString);
      int qmaxValue = Integer.parseInt(qmaxString);

      if (metadataExists(nameString)) {
        responseLabel.setText("Metadata already exists");
        return;
      }
      String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
      String url = "http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/add-variable";
      String param = String.format("?name=%s&unit=%s&period=%d&qmin=%d&qmax=%d",
          java.net.URLEncoder.encode(nameString, java.nio.charset.StandardCharsets.UTF_8),
          java.net.URLEncoder.encode(unitString, java.nio.charset.StandardCharsets.UTF_8),
          periodValue, qminValue, qmaxValue);
      String urlCompleted = url + param;

      HttpResponse<String> response = makePostRequest(urlCompleted, null);
      if (response.statusCode() == 200) {
        responseLabel.setText("Variable added successfully.");
        notifyMetadataUpdate();
      } else {
        responseLabel.setText("Failed to add variable: " + response.body());
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
    }
  }

  @FXML
  private void handleModify() {
    System.out.println(dataExists(name.getText()));
    if (dataExists(name.getText())) {
      responseLabel.setText("Data exists, cannot modify metadata");
    } else {
      String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
      String url = "http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/setMetadata";
      String form = String.format("?name=%s&unit=%s&period=%s&qmin=%s&qmax=%s",
          java.net.URLEncoder.encode(name.getText(), java.nio.charset.StandardCharsets.UTF_8),
          java.net.URLEncoder.encode(unit.getText(), java.nio.charset.StandardCharsets.UTF_8),
          period.getText(), qmin.getText(), qmax.getText());
      String urlCompleted = url + form;
      System.out.println("URL: " + urlCompleted);

      try {
        HttpResponse<String> response = makePostRequest(urlCompleted, null);
        if (response.statusCode() == 200) {
          responseLabel.setText("Metadata modified successfully.");
        } else {
          responseLabel.setText("Failed to modify metadata: " + response.body());
        }
      } catch (Exception e) {
        e.printStackTrace();
        responseLabel.setText("Error: " + e.getMessage());
      }
    }
  }

  private HttpResponse<String> makeDeleteRequest(String url) throws java.net.URISyntaxException, java.io.IOException, InterruptedException {
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(new java.net.URI(url))
        .DELETE()
        .build();

    return httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  @FXML
  private void handleDelete() {
    String nameString = name.getText();
    nameString.replaceAll(" ", "");
    if (metadataExists(nameString)) {
      Alert alert = new Alert(AlertType.CONFIRMATION);
      alert.setTitle("Delete Metadata");
      alert.setHeaderText("Are you sure you want to delete this metadata?");
      alert.setContentText("Choose your option.");

      ButtonType buttonTypeYesData = new ButtonType("Yes, delete metadata and data");
      ButtonType buttonTypeYesMetadata = new ButtonType("Yes, delete metadata only");
      ButtonType buttonTypeNo = new ButtonType("No", ButtonType.CANCEL.getButtonData());

      if (dataExists(nameString)) {
        alert.getButtonTypes().setAll(buttonTypeYesData, buttonTypeNo);
      } else {
        alert.getButtonTypes().setAll(buttonTypeYesMetadata, buttonTypeNo);
      }

      Optional<ButtonType> result = alert.showAndWait();
      if (result.isPresent()) {
        if (result.get() == buttonTypeYesData) {
          deleteMetadata(nameString, true);
        } else if (result.get() == buttonTypeYesMetadata) {
          deleteMetadata(nameString, false);
        } else {
          responseLabel.setText("Deletion cancelled.");
        }
      }
    } else {
      responseLabel.setText("Metadata does not exist.");
    }
  }

  private void deleteMetadata(String name, boolean deleteData) {
    try {
      String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
      String url = deleteData ?
          "http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/delete-observations" :
          "http://"+ipAddress+":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/delete-time_series";

      String query = String.format("?name=%s", java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8));
      HttpResponse<String> response = makeDeleteRequest(url + query);

      System.out.println("url " + url + query);

      if (response.statusCode() == 200) {
        responseLabel.setText(deleteData ? "Metadata and associated data deleted successfully." : "Metadata deleted successfully.");
        notifyMetadataUpdate();
      } else {
        responseLabel.setText("Failed to delete: " + response.body());
        System.out.println("Error: " + response.body());
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabel.setText("Error: " + e.getMessage());
    }
  }

  private void notifyMetadataUpdate() {
    // Notify ObservationsController and ConfigController to reload metadata
    Scene scene = name.getScene();
    Platform.runLater(() -> {
      MetadataUtil.loadMetadata((ComboBox<String>) getNodeById(scene,"metadataComboBox"), (Label) getNodeById(scene,"responseLabelObservations"));
      MetadataUtil.loadMetadata((ComboBox<String>) getNodeById(scene,"nameComboBox"), (Label) getNodeById(scene,"responseLabelConfig"));
    });
  }

  private Node getNodeById(Scene scene, String id) {
    if (scene == null || id == null) {
      return null;
    }
    return getNodeById(scene.getRoot(), id);
  }

  private Node getNodeById(Parent parent, String id) {
    if (parent == null || id == null) {
      return null;
    }

    for (Node node : parent.getChildrenUnmodifiable()) {
      if (id.equals(node.getId())) {
        return node;
      }
      if (node instanceof Parent) {
        Node found = getNodeById((Parent) node, id);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
