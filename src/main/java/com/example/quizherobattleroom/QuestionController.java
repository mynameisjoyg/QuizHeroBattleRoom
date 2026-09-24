package com.example.quizherobattleroom;


import com.example.quizherobattleroom.dto.Question;
import com.example.quizherobattleroom.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    // 呼叫範例：GET /api/questions?subject=Math&volume=1&chapter=2
    @GetMapping
    public ResponseEntity<List<Question>> getQuestions(
            @RequestParam String subject,
            @RequestParam Integer volume,
            @RequestParam Integer chapter) {

        try {
            List<Question> questions = questionService.getQuestions(subject, volume, chapter);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
