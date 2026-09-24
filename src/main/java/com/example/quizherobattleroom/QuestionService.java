package com.example.quizherobattleroom;

import com.example.quizherobattleroom.dto.Question;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class QuestionService {

    @Autowired
    private Firestore db;

    /**
     * 依據 科目(subject)、冊別(volume)、章節(chapter) 查詢題目列表
     */
    public List<Question> getQuestions(String subject, Integer volume, Integer chapter)
            throws ExecutionException, InterruptedException {

        List<Question> questionList = new ArrayList<>();

        // 1. 建立 Firestore 組合查詢 (NoSQL Filter)
        ApiFuture<QuerySnapshot> future = db.collection("questions")
                .whereEqualTo("subject", subject)
                .whereEqualTo("volume", volume)
                .whereEqualTo("chapter", chapter)
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
