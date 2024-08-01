package org.example.timeseriesview.controller;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import org.example.timeseriesview.util.ConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class Controller {

  @FXML
  private LineChart<Number, Number> lineChart;
  @FXML
  private CheckBox minCheckBox, maxCheckBox, medianCheckBox, avgCheckBox, quartCheckBox;
  @FXML
  private Label seriesNameLabel;

  private String seriesName;
  private String zoomType;

  private String zoomFactor;
  private List<String> configList;
  private int Nbv = 100;

  public void setSeriesConfigurations(String seriesName, String zoomType, List<String> configList, String serverIp) {
    this.seriesName = seriesName;
    this.zoomType = zoomType;
    this.configList = configList;
    seriesNameLabel.setText(seriesName + " (" + zoomType + ")");
    initializeChart(serverIp);
    updateCheckBoxes();
  }

  @FXML
  public void initialize() {
    // This method can be empty or used for general initialization
  }

  private void initializeChart(String serverIp) {
    String[] metadata = ConfigurationService.loadMetadata(serverIp, seriesName);
    if (metadata == null) {
      System.out.println("Failed to load metadata.");
      return;
    }

    String startDate = null;
    String endDate = null;
    int period = 1;

    for (String meta : metadata) {
      if (meta.startsWith("start_date:")) {
        startDate = meta.split(":")[1].trim();
      } else if (meta.startsWith("end_date:")) {
        endDate = meta.split(":")[1].trim();
      } else if (meta.startsWith("period:")) {
        period = Integer.parseInt(meta.split(":")[1].trim());
      }
    }

    if (startDate == null || endDate == null) {
      System.out.println("Failed to parse start or end date.");
      return;
    }

    Map<Integer, String> zoomParams = Map.of(period, zoomType);
    String[] initData = ConfigurationService.getInitView(serverIp, seriesName, zoomType, String.valueOf(Nbv), zoomParams);
    if (initData == null) {
      System.out.println("Failed to load initial view data.");
      return;
    }

    // Clear existing data
    lineChart.getData().clear();

    // Create series for each statistic
    XYChart.Series<Number, Number> minSeries = new XYChart.Series<>();
    XYChart.Series<Number, Number> maxSeries = new XYChart.Series<>();
    XYChart.Series<Number, Number> medianSeries = new XYChart.Series<>();
    XYChart.Series<Number, Number> avgSeries = new XYChart.Series<>();
    XYChart.Series<Number, Number> quartSeries = new XYChart.Series<>();

    // Parse init data
    ObjectMapper mapper = new ObjectMapper();
    for (int i = 0; i < initData.length; i++) {
      try {
        Map<String, Object> dataPoint = mapper.readValue(initData[i], Map.class);
        if (dataPoint.containsKey("MIN")) {
          minSeries.getData().add(new XYChart.Data<>(i * period, Double.parseDouble(dataPoint.get("MIN").toString())));
        }
        if (dataPoint.containsKey("MAX")) {
          maxSeries.getData().add(new XYChart.Data<>(i * period, Double.parseDouble(dataPoint.get("MAX").toString())));
        }
        if (dataPoint.containsKey("MEDIAN")) {
          medianSeries.getData().add(new XYChart.Data<>(i * period, Double.parseDouble(dataPoint.get("MEDIAN").toString())));
        }
        if (dataPoint.containsKey("AVG")) {
          avgSeries.getData().add(new XYChart.Data<>(i * period, Double.parseDouble(dataPoint.get("AVG").toString())));
        }
        if (dataPoint.containsKey("QUART")) {
          quartSeries.getData().add(new XYChart.Data<>(i * period, Double.parseDouble(dataPoint.get("QUART").toString())));
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // Add series to chart
    lineChart.getData().addAll(minSeries, maxSeries, medianSeries, avgSeries, quartSeries);

    // Set axis labels
    NumberAxis xAxis = (NumberAxis) lineChart.getXAxis();
    NumberAxis yAxis = (NumberAxis) lineChart.getYAxis();
    xAxis.setLabel("Time axis");
    yAxis.setLabel("Value axis");

    // Update xAxis range
    xAxis.setAutoRanging(false);
    xAxis.setLowerBound(0);
    xAxis.setUpperBound(initData.length * period);
    xAxis.setTickUnit(period);
  }

  private void updateCheckBoxes() {
    minCheckBox.setDisable(true);
    maxCheckBox.setDisable(true);
    medianCheckBox.setDisable(true);
    avgCheckBox.setDisable(true);
    quartCheckBox.setDisable(true);

    for (String config : configList) {
      if (config.contains("min")) {
        minCheckBox.setSelected(true);
      }
      if (config.contains("max")) {
        maxCheckBox.setSelected(true);
      }
      if (config.contains("median")) {
        medianCheckBox.setSelected(true);
      }
      if (config.contains("avg")) {
        avgCheckBox.setSelected(true);
      }
      if (config.contains("quart")) {
        quartCheckBox.setSelected(true);
      }
    }
  }

  @FXML
  private void handlePrevious() {
    // Implement navigation to previous data
  }

  @FXML
  private void handleNext() {
    // Implement navigation to next data
  }

  @FXML
  private void handleUp() {
    // Implement custom action for up
  }

  @FXML
  private void handleDown() {
    // Implement custom action for down
  }
}
