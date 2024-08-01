package org.example.timeseriesview;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.timeseriesview.util.ServerIpDialog;
import org.example.timeseriesview.util.ConfigurationService;

import java.util.*;

public class TimeSeriesApp extends Application {

  private static String serverIp;

  @Override
  public void start(Stage primaryStage) throws Exception {
    serverIp = ServerIpDialog.showAndWait();
    if (serverIp == null) {
      ServerIpDialog.showErrorAndExit("Server IP address is required. The application will now exit.");
      return;
    }

    List<String> configurations = ConfigurationService.getAvailableConfigurations(serverIp);
    if (configurations == null) {
      ServerIpDialog.showErrorAndExit("Failed to retrieve configurations from the server. The application will now exit.");
      return;
    }

    Map<String, Map<String, List<String>>> seriesConfigMap = organizeConfigurations(configurations);

    for (String seriesName : seriesConfigMap.keySet()) {
      Map<String, List<String>> zoomConfigMap = seriesConfigMap.get(seriesName);
      for (String zoomType : zoomConfigMap.keySet()) {
        List<String> configList = zoomConfigMap.get(zoomType);
        ViewerWindow viewer = new ViewerWindow();
        viewer.start(new Stage(), seriesName, zoomType, configList);
      }
    }
  }

  private Map<String, Map<String, List<String>>> organizeConfigurations(List<String> configurations) {
    Map<String, Map<String, List<String>>> seriesConfigMap = new HashMap<>();

    for (String config : configurations) {
      String[] parts = config.split("_");
      String seriesName = parts[0];
      String zoomCoef = parts[3];
      String zoomType = zoomCoef.equals("co") ? "factor" : "calendar";

      seriesConfigMap
          .computeIfAbsent(seriesName, k -> new HashMap<>())
          .computeIfAbsent(zoomType, k -> new ArrayList<>())
          .add(config);
    }

    return seriesConfigMap;
  }

  public static String getServerIp() {
    return serverIp;
  }

  public static void main(String[] args) {
    launch(args);
  }
}
