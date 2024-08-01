package org.example.timeseriesadmin.controller;


import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import org.example.timeseriesadmin.util.IpAddressManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


import static org.example.timeseriesadmin.util.ConfigurationListUtil.notifyConfigurationListUpdate;


public class ConfigurationsListController {

  @FXML
  private TableView<ConfigurationItem> configTable;
  @FXML
  private TableColumn<ConfigurationItem, String> nameColumn;
  @FXML
  private TableColumn<ConfigurationItem, Void> actionColumn;

  @FXML
  private Label responseLabelConfigList;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @FXML
  public void initialize() {
    nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    addButtonToTable();
    notifyConfigurationListUpdate(configTable,responseLabelConfigList);
  }

  private void addButtonToTable() {
    Callback<TableColumn<ConfigurationItem, Void>, TableCell<ConfigurationItem, Void>> cellFactory = new Callback<>() {
      @Override
      public TableCell<ConfigurationItem, Void> call(final TableColumn<ConfigurationItem, Void> param) {
        final TableCell<ConfigurationItem, Void> cell = new TableCell<>() {

          private final Button btn = new Button("Delete");

          {
            btn.setOnAction((event) -> {
              ConfigurationItem data = getTableView().getItems().get(getIndex());
              Alert alert = new Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
              alert.setTitle("Delete Configuration");
              alert.setHeaderText("Are you sure you want to delete this configuration?");
              alert.setContentText("Choose your option.");

              ButtonType buttonTypeYes = new ButtonType("Yes");
              ButtonType buttonTypeNo = new ButtonType("No", ButtonType.CANCEL.getButtonData());
              alert.getButtonTypes().setAll(buttonTypeYes, buttonTypeNo);

              java.util.Optional<ButtonType> result = alert.showAndWait();
              if (result.isPresent()) {
                if (result.get() == buttonTypeYes) {
                  deleteConfiguration(data.getName());
                } else {
                  responseLabelConfigList.setText("Deletion cancelled.");
                }
              }


            });
          }

          @Override
          public void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
              setGraphic(null);
            } else {
              setGraphic(btn);
            }
          }
        };
        return cell;
      }
    };

    actionColumn.setCellFactory(cellFactory);
  }

  private void deleteConfiguration(String name) {
    String ipAddress = IpAddressManager.getInstance().getIpAddress();
    String url = "http://" + ipAddress + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/delete-configuration/" + name;
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new URI(url))
          .DELETE()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        responseLabelConfigList.setText("Configuration deleted successfully.");
        notifyConfigurationListUpdate(configTable, responseLabelConfigList); // Reload configurations after deletion
      } else {
        responseLabelConfigList.setText("Failed to delete configuration: " + response.statusCode() + " - " + response.body());
        System.out.println("Failed to delete configuration: " + response.statusCode() + " - " + response.body());
        System.out.println("ConfigName: " + name);
      }
    } catch (Exception e) {
      e.printStackTrace();
      responseLabelConfigList.setText("Error: " + e.getMessage());
    }
  }


  public static class ConfigurationItem {
    private String name;

    // Getters et setters
    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }
  }
}
