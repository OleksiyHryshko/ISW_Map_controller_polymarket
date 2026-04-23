package com.example.filterbot.model;

public class TargetInfo {
    public String title;
    public String city;     // Name used for search in the sheet
    public double lat;      // Latitude (GPS)
    public double lon;      // Longitude (GPS)
    public String tokenId;  // "YES" contract token ID
    public long deadline;   // UNIX time when the bet expires

    public TargetInfo() {}

    public TargetInfo(String city, double lat, double lon, String tokenId, long deadline) {

        this.city = city;
        this.lat = lat;
        this.lon = lon;
        this.tokenId = tokenId;
        this.deadline = deadline;
    }
}