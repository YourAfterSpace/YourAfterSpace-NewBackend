package com.yourafterspace.yas_backend.dto;

import java.util.List;

/** Overall questionnaire progress: per-category percentages and total profile completion. */
public class QuestionnaireProgressResponse {

  private List<CategoryProgressDto> categories;
  private int totalAnswered;
  private int totalQuestions;
  private double totalPercentage;

  public QuestionnaireProgressResponse() {}

  public QuestionnaireProgressResponse(
      List<CategoryProgressDto> categories,
      int totalAnswered,
      int totalQuestions,
      double totalPercentage) {
    this.categories = categories;
    this.totalAnswered = totalAnswered;
    this.totalQuestions = totalQuestions;
    this.totalPercentage = totalPercentage;
  }

  public List<CategoryProgressDto> getCategories() {
    return categories;
  }

  public void setCategories(List<CategoryProgressDto> categories) {
    this.categories = categories;
  }

  public int getTotalAnswered() {
    return totalAnswered;
  }

  public void setTotalAnswered(int totalAnswered) {
    this.totalAnswered = totalAnswered;
  }

  public int getTotalQuestions() {
    return totalQuestions;
  }

  public void setTotalQuestions(int totalQuestions) {
    this.totalQuestions = totalQuestions;
  }

  public double getTotalPercentage() {
    return totalPercentage;
  }

  public void setTotalPercentage(double totalPercentage) {
    this.totalPercentage = totalPercentage;
  }
}
