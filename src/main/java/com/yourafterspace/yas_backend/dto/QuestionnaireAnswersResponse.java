package com.yourafterspace.yas_backend.dto;

import java.util.Map;

/** Response DTO for questionnaire answers. Keys are question ids; values are string or list of strings. */
public class QuestionnaireAnswersResponse {

  private Map<String, Object> answers;

  public QuestionnaireAnswersResponse() {}

  public QuestionnaireAnswersResponse(Map<String, Object> answers) {
    this.answers = answers;
  }

  public Map<String, Object> getAnswers() {
    return answers;
  }

  public void setAnswers(Map<String, Object> answers) {
    this.answers = answers;
  }
}
