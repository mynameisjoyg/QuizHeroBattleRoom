package com.example.quizherobattleroom.model;

public class AnswerMessage {
    private String questionId;      // 當前搶答的題目 ID
    private String selectedOption;  // 玩家選擇的答案（例如："玉山" 或 "A"）
    private long clientTimestamp;   // (可選) 玩家在手機上按下按鈕的毫秒時間戳

    // 必須要有無參數建構子 (Jackson 解析 JSON 時需要)
    public AnswerMessage() {
    }

    public AnswerMessage(String questionId, String selectedOption, long clientTimestamp) {
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.clientTimestamp = clientTimestamp;
    }

    // Getters and Setters
    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getSelectedOption() {
        return selectedOption;
    }

    public void setSelectedOption(String selectedOption) {
        this.selectedOption = selectedOption;
    }

    public long getClientTimestamp() {
        return clientTimestamp;
    }

    public void setClientTimestamp(long clientTimestamp) {
        this.clientTimestamp = clientTimestamp;
    }
}
