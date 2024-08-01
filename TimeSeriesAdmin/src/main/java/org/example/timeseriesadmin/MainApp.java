package org.example.timeseriesadmin;

import org.example.timeseriesadmin.controller.MainViewController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.TabPane;

public class MainApp extends Application {

    private Stage primaryStage;
    private String adresseIP;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("Data Management Application");

        showMainView();
    }

    public void showMainView() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("view/MainView.fxml"));
            TabPane mainLayout = (TabPane) loader.load();

            // Give the controller access to the main app
            MainViewController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);

            Scene scene = new Scene(mainLayout, 600, 600);
            scene.getStylesheets().add(getClass().getResource("/org/example/timeseriesadmin/css/style.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
