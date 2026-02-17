package com.yourafterspace.yas_backend.dto;

import java.util.List;

/** A category (e.g. Background, Interests) containing questions directly. */
public class CategoryDto {

  private String id;
  private String name;
  private String description;
  private List<QuestionDto> questions;

  /** Optional image URL for the category (e.g. for card or header). Can be absolute or relative. */
  private String imageUrl;

  /**
   * Weight of this category toward overall profile completion. Default 1.0 if null. Overall % =
   * sum(categoryWeight * categoryPercentage) / sum(categoryWeights) for categories with questions.
   */
  private Double weight;

  public CategoryDto() {}

  public CategoryDto(String id, String name, String description, List<QuestionDto> questions) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.questions = questions;
  }

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

  public List<QuestionDto> getQuestions() {
    return questions;
  }

  public void setQuestions(List<QuestionDto> questions) {
    this.questions = questions;
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
}
