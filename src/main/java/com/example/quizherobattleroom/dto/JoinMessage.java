package com.example.quizherobattleroom.dto;

public class JoinMessage {
    private String playerId;  // 玩家 ID (例如: "Android_Player_01")
    private String playerName; // (可選) 玩家顯示名稱

    // 必須要有無參數建構子 (Jackson 解析 JSON 時需要)
    public JoinMessage() {
    }

    public JoinMessage(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    // Getters and Setters
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}