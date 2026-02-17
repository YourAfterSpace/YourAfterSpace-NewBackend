package com.yourafterspace.yas_backend.dto;

import com.yourafterspace.yas_backend.dto.QuestionDto.QuestionType;
import java.util.List;

/**
 * Question with the user's answer stored on it. Used in profile: each category has questions, each
 * question has an answer (or null if not answered).
 */
public class QuestionWithAnswerDto {

  private String id;
  private String title;
  private String description;
  private QuestionType type;
  private List<String> options;
  private String categoryId;
  private String categoryName;
  private Double weight;
  /** User's answer for this question. String for TEXT/SINGLE_CHOICE, List of strings for MULTIPLE_CHOICE, or null if not answered. */
  private Object answer;

  public QuestionWithAnswerDto() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public QuestionType getType() {
    return type;
  }

  public void setType(QuestionType type) {
    this.type = type;
  }

  public List<String> getOptions() {
    return options;
  }

  public void setOptions(List<String> options) {
    this.options = options;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(String categoryId) {
    this.categoryId = categoryId;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String categoryName) {
    this.categoryName = categoryName;
  }

  public Double getWeight() {
    return weight;
  }

  public void setWeight(Double weight) {
    this.weight = weight;
  }

  public Object getAnswer() {
    return answer;
  }

  public void setAnswer(Object answer) {
    this.answer = answer;
  }
}
