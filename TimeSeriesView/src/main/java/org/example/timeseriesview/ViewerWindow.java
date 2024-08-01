package org.example.timeseriesview;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.timeseriesview.controller.Controller;

import java.util.List;

public class ViewerWindow {

  public void start(Stage stage, String seriesName, String zoomType, List<String> configList) throws Exception {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/timeseriesview/view/main.fxml"));
    Parent root = loader.load();

    Controller controller = loader.getController();
    controller.setSeriesConfigurations(seriesName, zoomType, configList, TimeSeriesApp.getServerIp());

    Scene scene = new Scene(root);
    stage.setTitle("Time Series Viewer - " + seriesName + " (" + zoomType + ")");
    stage.setScene(scene);
    stage.show();
  }
}
