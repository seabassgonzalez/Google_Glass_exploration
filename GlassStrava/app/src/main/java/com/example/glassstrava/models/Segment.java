package com.example.glassstrava.models;

import org.json.JSONObject;
import java.util.List;

/**
 * Model class for Strava Segment
 */
public class Segment {
    public long id;
    public String name;
    public double distance;
    public double averageGrade;
    public double maximumGrade;
    public double elevationHigh;
    public double elevationLow;
    public int climbCategory;
    public String city;
    public String state;
    public String country;
    public double startLatitude;
    public double startLongitude;
    public double endLatitude;
    public double endLongitude;
    public List<SegmentEffort> leaderboard;
    
    public static Segment fromJson(JSONObject json) {
        Segment segment = new Segment();
        
        try {
            segment.id = json.getLong("id");
            segment.name = json.getString("name");
            segment.distance = json.getDouble("distance");
            segment.averageGrade = json.optDouble("average_grade", 0);
            segment.maximumGrade = json.optDouble("maximum_grade", 0);
            segment.elevationHigh = json.optDouble("elevation_high", 0);
            segment.elevationLow = json.optDouble("elevation_low", 0);
            segment.climbCategory = json.optInt("climb_category", 0);
            segment.city = json.optString("city", "");
            segment.state = json.optString("state", "");
            segment.country = json.optString("country", "");
            
            if (json.has("start_latlng")) {
                org.json.JSONArray startLatLng = json.getJSONArray("start_latlng");
                segment.startLatitude = startLatLng.getDouble(0);
                segment.startLongitude = startLatLng.getDouble(1);
            }
            
            if (json.has("end_latlng")) {
                org.json.JSONArray endLatLng = json.getJSONArray("end_latlng");
                segment.endLatitude = endLatLng.getDouble(0);
                segment.endLongitude = endLatLng.getDouble(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return segment;
    }
}