package com.example.quizherobattleroom.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @Bean
    public Firestore firestore() throws Exception {
        InputStream serviceAccountStream;

        // 1. 優先從系統環境變數讀取 JSON 字串
        String firebaseConfigEnv = System.getenv("FIREBASE_CONFIG_JSON");

        if (firebaseConfigEnv != null && !firebaseConfigEnv.trim().isEmpty()) {
            // 將環境變數中的 JSON 字串轉為 InputStream
            serviceAccountStream = new ByteArrayInputStream(
                    firebaseConfigEnv.getBytes(StandardCharsets.UTF_8)
            );
        } else {
            // 2. 備用方案：若無環境變數，讀取本地 src/main/resources/serviceAccountKey.json
            ClassPathResource resource = new ClassPathResource("serviceAccountKey.json");
            if (resource.exists()) {
                serviceAccountStream = resource.getInputStream();
            } else {
                throw new IllegalStateException(
                        "未找到 Firebase 憑證！請設定 FIREBASE_CONFIG_JSON 環境變數，或於 resources 提供 serviceAccountKey.json"
                );
            }
        }

        // 3. 初始化 FirebaseApp
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        // 4. 回傳 Firestore 實例
        return FirestoreClient.getFirestore();
    }
}
