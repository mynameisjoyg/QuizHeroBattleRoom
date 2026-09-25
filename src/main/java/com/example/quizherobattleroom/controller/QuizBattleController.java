package com.example.quizherobattleroom.controller;

import com.example.quizherobattleroom.dto.JoinMessage;
import com.example.quizherobattleroom.dto.MatchRequest; // 💡 引入你的 MatchRequest DTO
import com.example.quizherobattleroom.model.AnswerMessage;
import com.example.quizherobattleroom.model.GameRoom;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
public class QuizBattleController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 簡易配對等待隊列
    private final ConcurrentLinkedQueue<String> matchQueue = new ConcurrentLinkedQueue<>();
    // 管理活躍中的對戰房間
    private final ConcurrentHashMap<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    // 紀錄 SessionId -> 玩家資訊 (RoomId & PlayerId)
    public static final Map<String, QuizBattleController.UserSessionInfo> sessionMap = new ConcurrentHashMap<>();

    private String playerId = "";
    private String subject = "English";
    private String volume = "1";
    private String chapter = "1";
    private String id = "";
    private String question = "";
    private String correctAnswer ="";



    /**
     * 1. 玩家請求配對 (改為傳入 MatchRequest DTO)
     */
    @MessageMapping("/matchmaking")
    public void processMatchmaking(@Payload MatchRequest request) {
        System.out.println("JOYG: processMatchmaking");
        // 從前端傳送的 JSON 讀取 playerId (防呆處理：若為 null 則自動給予預設 ID)
        playerId = (request != null && request.getPlayerId() != null && !request.getPlayerId().isBlank())
                ? request.getPlayerId()
                : "Player_" + UUID.randomUUID().toString().substring(0, 5);
        subject = (request != null && request.getPlayerId() != null)? request.getSubject():"English";
        volume = (request != null && request.getVolume() != null)? request.getVolume():"1";
        chapter = (request != null && request.getChapter() != null)? request.getChapter():"1";

        System.out.println("--------------------------------------------------");
        System.out.println("📩 [後端收到配對請求] 玩家 ID: " + playerId + ", subject="+subject+", volume="+volume+", chapter="+chapter);

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
                messagingTemplate.convertAndSend("/topic/matchmaking", Optional.of(matchPayload));


                // 配對成功 3 秒後發送第一題
                CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
                    try {
                        sendNextQuestion(roomId, subject, volume, chapter);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    /**
     * 2. 發送題目 (帶有伺服器毫秒時間戳)
     */
    private void sendNextQuestion(String roomId, String subject, String volume, String chapter) throws ExecutionException, InterruptedException {
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return;

        long serverTimestamp = System.currentTimeMillis();
        String questionId = "Q_" + UUID.randomUUID().toString().substring(0, 5);

        // 重置題目狀態與記錄時間戳
        room.resetQuestion(questionId, serverTimestamp);


        //////////////取得題庫
        Firestore db;
        try {
            // 1. 載入憑證檔 (請確保路徑正確，或改用 ClassLoader 讀取 resources)
            InputStream serviceAccount = new FileInputStream("src/main/resources/serviceAccountKey.json");

            // 2. 設定 FirebaseOptions
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 3. 避免重複初始化 (若 FirebaseApp 尚未初始化才執行)
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            // 4. 取得 Firestore 實例
            db = FirestoreClient.getFirestore();

        } catch (Exception e) {
            throw new RuntimeException("初始化 Firestore 失敗: " + e.getMessage(), e);
        }
        // 1. 發起非同步查詢，獲取 ApiFuture
        ApiFuture<QuerySnapshot> future = db.collection("English_Quiz").get();

        // 2. 呼叫 .get() 阻塞等待並取得 QuerySnapshot
        QuerySnapshot querySnapshot = future.get();

        // 3. 取得所有 DocumentSnapshot 清單
        List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();

        if (documents.isEmpty()) {
            System.out.println("No questions found in 'English' collection.");
            return;
        }

        // 4.1 取出第一個 document 的資料 (如你原本寫法的 document[0])
        Random random = new Random();
        QueryDocumentSnapshot firstDocument = documents.get(random.nextInt(documents.size()) + 0);

        // 安全取得欄位字串 (使用 getString 可避免 NPE 或 toString 轉型錯誤)
        //String id = firstDocument.getString("id");
        id = firstDocument.getLong("id").toString();
        question = firstDocument.getString("question");
        correctAnswer = firstDocument.getString("answer");

        System.out.println("=== 第一題 ===");
        System.out.println("id: " + id);
        System.out.println("Question: " + question);
        System.out.println("Answer: " + correctAnswer);

        String fullQuestion = question;

        // 1. 擷取選項
        Pattern optionPattern = Pattern.compile("\\([A-D]\\)\\s*[^\\(\\)]+");
        Matcher matcher = optionPattern.matcher(fullQuestion);

        List<String> options = new ArrayList<>();
        int firstOptionIndex = -1;

        while (matcher.find()) {
            if (firstOptionIndex == -1) {
                firstOptionIndex = matcher.start(); // 記錄第一個選項 (A) 開始的位置
            }
            options.add(matcher.group().trim());
        }

        // 2. 擷取不含選項的題目主幹
        String stem = (firstOptionIndex != -1) ? fullQuestion.substring(0, firstOptionIndex).trim() : fullQuestion.trim();

        String optionA = options.get(0);
        String optionB = options.get(1);
        String optionC = options.get(2);
        String optionD = options.get(3);

        // 輸出結果
        System.out.println("stem = " + stem);
        System.out.println("optionA = " + optionA);
        System.out.println("optionB = " + optionB);
        System.out.println("optionC = " + optionC);
        System.out.println("optionD = " + optionD);
        //////////////取得題庫

        Map<String, Object> quizPayload = Map.of(
                "type", "QUESTION",
                "questionId", id,
                "title", stem,
                "options", List.of(optionA, optionB, optionC, optionD),
                "serverTimestamp", serverTimestamp // 帶上發題時間戳
        );

        messagingTemplate.convertAndSend("/topic/room/"+roomId + "/quiz", Optional.of(quizPayload));
    }

    /**
     * 3. 玩家搶答比對 (毫秒級計算)
     */
    @MessageMapping("/room/{roomId}/answer")
    public void handleAnswer(@DestinationVariable String roomId, AnswerMessage answer, Principal principal) {
        GameRoom room = activeRooms.get(roomId);
        if (room == null) return;

        long receiveTimestamp = System.currentTimeMillis(); // 後端收到答案的毫秒時間戳
        // ✅ 使用前端傳過來的 playerId 作為 playerId
        String playerId = answer.getPlayerId();

        // 使用 AtomicBoolean 確保「第一個到達後端的請求」獲得搶答權
        if (room.lockAnswer()) {
            // 計算反應時間 (從發題到後端收到搶答的毫秒數)
            long reactionTimeMs = receiveTimestamp - room.getQuestionSentTimestamp();

            boolean isCorrect = correctAnswer.equals(answer.getSelectedOption()); // 驗證答案
            System.out.println("JOYG: correctAnswer="+correctAnswer+", answer.getSelectedOption()="+ answer.getSelectedOption()+", isCorrect="+isCorrect);

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
            //messagingTemplate.convertAndSendToUser(playerId, "/topic/quiz/notifications", failPayload);
            messagingTemplate.convertAndSendToUser(playerId, "/topic/room/" + roomId + "/result", failPayload);
        }
        // 答完5 秒後發送下一題
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            try {
                sendNextQuestion(roomId, this.subject, this.volume, this.chapter);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

    }

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId, JoinMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        // 記錄這組 sessionId 對應的 roomId 與 playerId
        this.sessionMap.put(sessionId, new UserSessionInfo(roomId, message.getPlayerId()));

        // ... 原有的加入房間邏輯 ...
    }


    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UserSessionInfo sessionInfo = sessionMap.remove(sessionId);

        if (sessionInfo != null) {
            String roomId = sessionInfo.getRoomId();
            String disconnectedPlayerId = sessionInfo.getPlayerId();

            // 封包內容：通知房間內所有人有玩家離開
            Map<String, Object> leaveNotice = Map.of(
                    "type", "PLAYER_LEFT",
                    "message", "對手已離開對戰或連線中斷",
                    "leftPlayerId", disconnectedPlayerId
            );

            // 廣播給該房間內留著的對手
            messagingTemplate.convertAndSend("/topic/room/" + roomId, Optional.of(leaveNotice));
            System.out.println("玩家 " + disconnectedPlayerId + " 斷線，已廣播退場通知至房間: " + roomId);
        }
    }

    // 內部類別用於記錄 Session 綁定資訊
    public static class UserSessionInfo {
        private final String roomId;
        private final String playerId;

        public UserSessionInfo(String roomId, String playerId) {
            this.roomId = roomId;
            this.playerId = playerId;
        }

        public String getRoomId() { return roomId; }
        public String getPlayerId() { return playerId; }
    }
}