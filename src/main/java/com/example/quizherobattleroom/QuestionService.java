package com.example.quizherobattleroom;

import com.example.quizherobattleroom.dto.Question;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QuestionService {

    @Autowired
    private Firestore db;

    /**
     * 依據 科目(subject)、冊別(volume)、章節(chapter) 查詢題目列表
     */
    public List<Question> getQuestions(String subject, Integer volume, Integer chapter)
            throws ExecutionException, InterruptedException {
        System.out.println("JOYGSAY: In QuestionService.java, getQuestions()");
        //////////////////////////////////
        //先取得Subject內的問題起始ID以及問題總數
        int QuestionFirstId = 1;
        int QuestionCount = 1;
        ApiFuture<DocumentSnapshot> metaFuture = db.collection("MetaData").document(subject).get();
        DocumentSnapshot document = metaFuture.get();

        // 2. 確認文件存在
        if (document.exists()) {
            // 取出欄位數值（Firestore 中的整數預設儲存為 Long）
            Long firstIdLong = document.getLong("Volume"+volume+"Chapter"+chapter+"QuestionFirstId");
            Long countLong = document.getLong("Volume"+volume+"Chapter"+chapter+"QuestionCount");

            // 轉成 int 並處理 null 預設值
            QuestionFirstId = (firstIdLong != null) ? firstIdLong.intValue() : 0;
            QuestionCount = (countLong != null) ? countLong.intValue() : 0;

            System.out.println("QuestionFirstId: " + QuestionFirstId);
            System.out.println("QuestionCount: " + QuestionCount);

        } else {
            System.out.println("找不到 Document 名稱為 English 的 MetaData 資料");
        }
        int randomQuestionId = ThreadLocalRandom.current().nextInt(1, QuestionCount + 1) + QuestionFirstId;
        //////////////////////////////////


        List<Question> questionList = new ArrayList<>();

        // 1. 建立 Firestore 組合查詢 (NoSQL Filter)
        ApiFuture<QuerySnapshot> future = db.collection("questions")
                .whereEqualTo("subject", subject)
                .whereEqualTo("volume", volume)
                .whereEqualTo("chapter", chapter)
                .whereEqualTo("id", randomQuestionId)
                .limit(1)
                .get();

        // 2. 非同步等待取得查詢結果文件列表
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        // 3. 將 Firestore Document 轉換為 Java 物件
        for (QueryDocumentSnapshot doc : documents) {
            Question q = doc.toObject(Question.class);
            q.setId(doc.getId()); // 補上 Document ID
            questionList.add(q);
        }

        return questionList;
    }
}
