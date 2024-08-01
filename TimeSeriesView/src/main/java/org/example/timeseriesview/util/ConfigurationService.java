package org.example.timeseriesview.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

import java.util.List;
import java.util.Arrays;



public class ConfigurationService {

  private static HttpClient httpClient = HttpClient.newHttpClient();
  public static List<String> getAvailableConfigurations(String serverIp) {
    if (serverIp == null || serverIp.isEmpty()) {
      System.out.println("IP Address is not set. Please set it in the Settings tab.");
      return null;
    }
    String url = "http://" + serverIp + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-configurations";
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new URI(url))
          .header("Content-Type", "application/json")
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String responseBody = response.body();
        String[] lines = responseBody.split("\n");

        return Arrays.asList(lines);
      } else {
        System.out.println("Failed to load configurations: " + response.statusCode() + " - " + response.body());
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error: " + e.getMessage());
    }

    return null;
  }

  public static String [] loadMetadata(String serverIp, String configurationName) {
    String url = "http://" + serverIp + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-metadata/" + configurationName;
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) {
          return null;
        }
        String [] result  = responseBody.split("\n");
        return result;
      } else {
        System.out.println("Failed to load metadata: " + response.statusCode() + " - " + response.body());
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error: " + e.getMessage());
    }
    return null;
  }

  public static String[] getInitView(String serverIp, String name, String aggregation, String nbv, Map<Integer, String> params) {
    String url = "http://" + serverIp + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-initview/?nbv=%s&name=%s&aggregation=%s";
    url = String.format(url, nbv, name, aggregation);

    // Convert params map to JSON string
    ObjectMapper mapper = new ObjectMapper();
    String jsonParams = "";
    try {
      jsonParams = mapper.writeValueAsString(params);
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error converting params to JSON: " + e.getMessage());
      return null;
    }

    // Add JSON string as a query parameter
    url += "&zoom=" + java.net.URLEncoder.encode(jsonParams, java.nio.charset.StandardCharsets.UTF_8);

    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .header("Content-Type", "application/json")
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) {
          return null;
        }
        String[] result = responseBody.split("\n");
        return result;
      } else {
        System.out.println("Failed to load metadata: " + response.statusCode() + " - " + response.body());
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error: " + e.getMessage());
    }
    return null;
  }

  public static String [] getView(String serverIp, String configurationName) {
    // TODO
    String url = "http://" + serverIp + ":8081/fr.ubo.fast.data.provider-0.0.1-SNAPSHOT/data-setup/get-metadata/" + configurationName;
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new java.net.URI(url))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) {
          return null;
        }
        String [] result  = responseBody.split("\n");
        return result;
      } else {
        System.out.println("Failed to load metadata: " + response.statusCode() + " - " + response.body());
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error: " + e.getMessage());
    }
    return null;
  }

}
