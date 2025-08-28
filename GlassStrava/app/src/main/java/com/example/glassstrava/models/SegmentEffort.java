package com.example.glassstrava.models;

import org.json.JSONObject;

/**
 * Model class for Strava Segment Effort (leaderboard entry)
 */
public class SegmentEffort {
    public long id;
    public String athleteName;
    public long athleteId;
    public int elapsedTime;
    public int movingTime;
    public String startDate;
    public int rank;
    
    public static SegmentEffort fromJson(JSONObject json) {
        SegmentEffort effort = new SegmentEffort();
        
        try {
            effort.athleteName = json.getString("athlete_name");
            effort.elapsedTime = json.getInt("elapsed_time");
            effort.movingTime = json.optInt("moving_time", effort.elapsedTime);
            effort.startDate = json.optString("start_date", "");
            effort.rank = json.optInt("rank", 0);
            
            if (json.has("effort_id")) {
                effort.id = json.getLong("effort_id");
            }
            
            if (json.has("athlete_id")) {
                effort.athleteId = json.getLong("athlete_id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return effort;
    }
}