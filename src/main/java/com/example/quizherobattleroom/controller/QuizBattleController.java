package com.example.quizherobattleroom.controller;

import com.example.quizherobattleroom.dto.MatchRequest; // 💡 引入你的 MatchRequest DTO
import com.example.quizherobattleroom.model.AnswerMessage;
import com.example.quizherobattleroom.model.GameRoom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Controller
public class QuizBattleController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 簡易配對等待隊列
    private final ConcurrentLinkedQueue<String> matchQueue = new ConcurrentLinkedQueue<>();
    // 管理活躍中的對戰房間
    private final ConcurrentHashMap<String, GameRoom> activeRooms = new ConcurrentHashMap<>();

    /**
     * 1. 玩家請求配對 (改為傳入 MatchRequest DTO)
     */
    @MessageMapping("/matchmaking")
    public void processMatchmaking(@Payload MatchRequest request) {
        System.out.println("JOYG: processMatchmaking");
        // 從前端傳送的 JSON 讀取 playerId (防呆處理：若為 null 則自動給予預設 ID)
        String playerId = (request != null && request.getPlayerId() != null && !request.getPlayerId().isBlank())
                ? request.getPlayerId()
                : "Player_" + UUID.randomUUID().toString().substring(0, 5);

        System.out.println("--------------------------------------------------");
        System.out.println("📩 [後端收到配對請求] 玩家 ID: " + playerId);

        synchronized (matchQueue) {
            if (!matchQueue.contains(playerId)) {
                matchQueue.add(playerId);
            }

            // 當隊列有 2 人時，成立對戰房間
            if (matchQueue.size() >= 2) {
                String p1 = matchQueue.poll();
                String p2 = matchQueue.poll();
                String roomId = UUID.randomUUID().toString();

                GameRoom room = new GameRoom(roomId, p1, p2);
                activeRooms.put(roomId, room);

                // 通知兩位玩家配對成功
                Map<String, Object> matchPayload = Map.of(
                        "status", "MATCHED",
                        "roomId", roomId,
                        "player1", p1,
                        "player2", p2
                );

                // 廣播給配對到的玩家頻道
                //messagingTemplate.convertAndSend("/topic/room/" + roomId, Optional.of(matchPayload));
                messagingTemplate.convertAndSend("/topic/room/0956520537", Optional.of(matchPayload));


                // 配對成功 3 秒後發送第一題
                CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
                    sendNextQuestion(roomId);
                });
            }
        }
    }

    /**
     * 2. 發送題目 (帶有伺服器毫秒時間戳)
     */
    private void sendNextQuestion(String roomId) {
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return;

        long serverTimestamp = System.currentTimeMillis();
        String questionId = "Q_" + UUID.randomUUID().toString().substring(0, 5);

        // 重置題目狀態與記錄時間戳
        room.resetQuestion(questionId, serverTimestamp);

        Map<String, Object> quizPayload = Map.of(
                "type", "QUESTION",
                "questionId", questionId,
                "title", "台灣最高的山是什麼山？",
                "options", List.of("玉山", "雪山", "陽明山", "阿里山"),
                "serverTimestamp", serverTimestamp // 帶上發題時間戳
        );

        messagingTemplate.convertAndSend("/topic/room/0956520537" + "/quiz", Optional.of(quizPayload));
    }

    /**
     * 3. 玩家搶答比對 (毫秒級計算)
     */
    @MessageMapping("/room/{roomId}/answer")
    public void handleAnswer(@DestinationVariable String roomId, AnswerMessage answer, Principal principal) {
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return;

        long receiveTimestamp = System.currentTimeMillis(); // 後端收到答案的毫秒時間戳
        String playerId = (principal != null) ? principal.getName() : "UnknownPlayer";

        // 使用 AtomicBoolean 確保「第一個到達後端的請求」獲得搶答權
        if (room.lockAnswer()) {
            // 計算反應時間 (從發題到後端收到搶答的毫秒數)
            long reactionTimeMs = receiveTimestamp - room.getQuestionSentTimestamp();

            boolean isCorrect = "玉山".equals(answer.getSelectedOption()); // 驗證答案

            Map<String, Object> resultPayload = Map.of(
                    "type", "ANSWER_RESULT",
                    "winnerId", playerId,
                    "isCorrect", isCorrect,
                    "reactionTimeMs", reactionTimeMs,
                    "serverTime", receiveTimestamp
            );

            // 廣播搶答結果給房間內所有人
            //messagingTemplate.convertAndSend("/topic/room/" + roomId + "/result", resultPayload);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/result", (Object) resultPayload);

        } else {
            // 晚一步搶答的玩家，只收通知（不計分）
            Map<String, Object> failPayload = Map.of(
                    "type", "ANSWER_TOO_LATE",
                    "playerId", playerId,
                    "message", "對手搶先一步回答了！"
            );
            messagingTemplate.convertAndSendToUser(playerId, "/topic/quiz/notifications", failPayload);
        }
    }
}