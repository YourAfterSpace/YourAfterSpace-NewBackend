package com.yourafterspace.yas_backend.dto;

import java.util.Map;

/**
 * Response for GET category with user's answers for that category only. Use when opening a
 * category screen: questions + existing answers for that category.
 */
public class CategoryWithAnswersResponse {

  private CategoryDto category;
  private Map<String, Object> answers;

  public CategoryWithAnswersResponse() {}

  public CategoryWithAnswersResponse(CategoryDto category, Map<String, Object> answers) {
    this.category = category;
    this.answers = answers;
  }

  public CategoryDto getCategory() {
    return category;
  }

  public void setCategory(CategoryDto category) {
    this.category = category;
  }

  public Map<String, Object> getAnswers() {
    return answers;
  }

  public void setAnswers(Map<String, Object> answers) {
    this.answers = answers;
  }
}
