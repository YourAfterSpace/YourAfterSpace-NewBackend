package com.yourafterspace.yas_backend.dto;

import java.util.List;

/**
 * Category with all its questions; each question includes the user's answer (or null). Stored in
 * user profile so each user has their own copy of categories and answers.
 */
public class CategoryWithQuestionsAndAnswersDto {

  private String id;
  private String name;
  private String description;
  private Double weight;
  private String imageUrl;
  private List<QuestionWithAnswerDto> questions;

  public CategoryWithQuestionsAndAnswersDto() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Double getWeight() {
    return weight;
  }

  public void setWeight(Double weight) {
    this.weight = weight;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public List<QuestionWithAnswerDto> getQuestions() {
    return questions;
  }

  public void setQuestions(List<QuestionWithAnswerDto> questions) {
    this.questions = questions;
  }
}
