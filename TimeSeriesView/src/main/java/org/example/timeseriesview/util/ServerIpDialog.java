package org.example.timeseriesview.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public class ServerIpDialog {

  public static String showAndWait() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Server IP Address");
    dialog.setHeaderText("Enter the server IP address:");
    dialog.setContentText("IP Address:");

    Optional<String> result = dialog.showAndWait();
    if (result.isPresent()) {
      return result.get();
    } else {
      return null;
    }
  }

  public static void showErrorAndExit(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
    alert.showAndWait();
    Platform.exit();
  }
}

