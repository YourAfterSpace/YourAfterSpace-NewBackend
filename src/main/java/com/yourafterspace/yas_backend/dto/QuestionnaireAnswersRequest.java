package com.yourafterspace.yas_backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request DTO for submitting questionnaire answers. Keys are question ids; values are either a
 * single string (single-choice) or a list of strings (multi-choice).
 *
 * <p>Example JSON: { "answers": { "gender": "female", "interests": ["hiking", "music", "travel"]
 * } }
 */
public class QuestionnaireAnswersRequest {

  @NotNull(message = "Answers map is required")
  private Map<String, Object> answers;

  public QuestionnaireAnswersRequest() {}

  public QuestionnaireAnswersRequest(Map<String, Object> answers) {
    this.answers = answers;
  }

  public Map<String, Object> getAnswers() {
    return answers;
  }

  public void setAnswers(Map<String, Object> answers) {
    this.answers = answers;
  }
}
