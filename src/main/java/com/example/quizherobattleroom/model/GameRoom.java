package com.example.quizherobattleroom.model;

import java.util.concurrent.atomic.AtomicBoolean;

public class GameRoom {
    private String roomId;
    private String player1Id;
    private String player2Id;

    // 記錄當前題目資訊
    private String currentQuestionId;
    private long questionSentTimestamp; // 發題的伺服器毫秒時間戳
    private AtomicBoolean isAnswered = new AtomicBoolean(false); // 確保一題只能被搶答一次

    public GameRoom(String roomId, String player1Id, String player2Id) {
        this.roomId = roomId;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
    }

    // Getter & Setter ...
    public boolean lockAnswer() {
        return isAnswered.compareAndSet(false, true); // CAS 確保單執行緒優先奪得
    }

    public void resetQuestion(String questionId, long timestamp) {
        this.currentQuestionId = questionId;
        this.questionSentTimestamp = timestamp;
        this.isAnswered.set(false);
    }

// ==========================================
    // 補上 Getter & Setter 方法
    // ==========================================

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getPlayer1Id() {
        return player1Id;
    }

    public void setPlayer1Id(String player1Id) {
        this.player1Id = player1Id;
    }

    public String getPlayer2Id() {
        return player2Id;
    }

    public void setPlayer2Id(String player2Id) {
        this.player2Id = player2Id;
    }

    public String getCurrentQuestionId() {
        return currentQuestionId;
    }

    public void setCurrentQuestionId(String currentQuestionId) {
        this.currentQuestionId = currentQuestionId;
    }

    // 👈 補上這個方法，Controller 就不會報錯了！
    public long getQuestionSentTimestamp() {
        return questionSentTimestamp;
    }

    public void setQuestionSentTimestamp(long questionSentTimestamp) {
        this.questionSentTimestamp = questionSentTimestamp;
    }

    public AtomicBoolean getIsAnswered() {
        return isAnswered;
    }
}
