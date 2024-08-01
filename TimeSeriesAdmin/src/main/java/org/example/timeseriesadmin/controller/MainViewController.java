package org.example.timeseriesadmin.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.scene.Scene;

import java.io.IOException;

public class MainViewController {

  private Stage primaryStage;

  @FXML
  private AnchorPane metadataPane;

  @FXML
  private AnchorPane observationsPane;

  @FXML
  private AnchorPane configPane;

  @FXML
  private AnchorPane settingsPane;

  @FXML
  private AnchorPane configListPane;

  public void setPrimaryStage(Stage primaryStage) {
    this.primaryStage = primaryStage;
  }

  @FXML
  public void initialize() {
    loadViewIntoPane("view/SettingsIPView.fxml", settingsPane);
    loadViewIntoPane("view/MetadataView.fxml", metadataPane);
    loadViewIntoPane("view/ObservationsView.fxml", observationsPane);
    loadViewIntoPane("view/ConfigurationsView.fxml", configPane);
    loadViewIntoPane("view/ConfigurationsListView.fxml", configListPane);

    disableTabsIfIpNotSet();
  }

  private void loadViewIntoPane(String fxmlFile, AnchorPane pane) {
    try {
      FXMLLoader loader = new FXMLLoader();
      loader.setLocation(getClass().getResource("/org/example/timeseriesadmin/" + fxmlFile));
      GridPane view = loader.load();
      pane.getChildren().setAll(view);

      // Pass the MainViewController reference to the child controller
      Object controller = loader.getController();
      if (controller instanceof SettingsIPController) {
        ((SettingsIPController) controller).setMainController(this);
      }
      if(controller instanceof ObservationsController) {
        ((ObservationsController) controller).setMainController(this);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void disableTabsIfIpNotSet() {
    String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
    boolean ipNotSet = ipAddress == null || ipAddress.isEmpty();

    metadataPane.setDisable(ipNotSet);
    observationsPane.setDisable(ipNotSet);
    configPane.setDisable(ipNotSet);
    configListPane.setDisable(ipNotSet);
  }

  @FXML
  private void handleShowConfigurations() {
    try {
      FXMLLoader loader = new FXMLLoader();
      loader.setLocation(getClass().getResource("/org/example/timeseriesadmin/view/ConfigurationsView.fxml"));
      AnchorPane pane = loader.load();
      Stage stage = new Stage();
      stage.setTitle("Configurations");
      stage.setScene(new Scene(pane));
      stage.show();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void checkAndEnableTabs() {
    String ipAddress = org.example.timeseriesadmin.util.IpAddressManager.getInstance().getIpAddress();
    boolean ipNotSet = ipAddress == null || ipAddress.isEmpty();

    metadataPane.setDisable(ipNotSet);
    observationsPane.setDisable(ipNotSet);
    configPane.setDisable(ipNotSet);
    configListPane.setDisable(ipNotSet);
  }

  public void disableSettingsTab() {
    settingsPane.setDisable(true);
  }

  public boolean checkStateObservations() {
    return !observationsPane.isDisabled();
  }
}
