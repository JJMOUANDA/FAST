package org.example.timeseriesadmin.util;

public class IpAddressManager {
  private static IpAddressManager instance;
  private String ipAddress;

  private IpAddressManager() {}

  public static IpAddressManager getInstance() {
    if (instance == null) {
      instance = new IpAddressManager();
    }
    return instance;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }
}
