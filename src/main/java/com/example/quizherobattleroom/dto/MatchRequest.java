package com.example.quizherobattleroom.dto;

public class MatchRequest {

    // 欄位名稱必須與前端 JSON 的 Key 完全一致（如 {"playerId": "Player_A"}）
    private String playerId ="";
    private String subject="";
    private String volume="";
    private String chapter="";

    // 1. 必須提供無參數建構子 (Jackson 反序列化 JSON 時所需)
    public MatchRequest() {
    }

    // 2. 帶參數建構子 (方便內部測試建立物件)
    public MatchRequest(String playerId, String subject, String volume, String chapter) {
        this.playerId = playerId;
        this.subject = subject;
        this.volume = volume;
        this.chapter = chapter;
    }

    // 3. Getter & Setter (Jackson 注入與讀取屬性所需)
    public String getPlayerId() {
        return playerId;
    }
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getSubject(){ return this.subject; }
    public void setSubject(){this.subject=subject;}

    public String getVolume(){ return this.volume; }
    public void setSubject(String volume){this.volume=volume;}

    public String getChapter(){ return this.chapter; }
    public void setChapter(){this.chapter=chapter;}

    @Override
    public String toString() {
        return "MatchRequest{" +
                "playerId='" + playerId + '\'' +
                '}';
    }
}
