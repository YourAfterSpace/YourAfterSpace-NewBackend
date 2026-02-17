package com.yourafterspace.yas_backend.dto;

import java.util.List;

/**
 * DTO for a single question. Frontend uses {@code type} to choose display: text input, single
 * select, multi select, searchable multi select, or rating.
 */
public class QuestionDto {

  /** Unique question id. Use as key when submitting answers (POST /v1/user/questionnaire). */
  private String id;

  /** Display title (e.g. "Fluent languages"). */
  private String title;

  /** Display description (e.g. "What languages do you speak fluently?"). */
  private String description;

  /**
   * How to display and collect answer: TEXT, SINGLE_CHOICE, MULTIPLE_CHOICE, RATING. Frontend can
   * show a search bar for multi-choice when options count is large.
   */
  private QuestionType type;

  /** Options for choice types. For RATING, can be min/max or labels (frontend convention). */
  private List<String> options;

  /** Category this question belongs to. */
  private String categoryId;

  private String categoryName;

  /**
   * Weight of this question within its category (for category completion %). Default 1.0 if null.
   * Category completion = sum(weight of answered questions) / sum(weight of all questions) * 100.
   */
  private Double weight;

  public QuestionDto() {}

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

  /**
   * Frontend display types: TEXT = text input, SINGLE_CHOICE = one option, MULTIPLE_CHOICE = multi
   * select (frontend may show search when options count is large), RATING = rating (e.g. 1-5).
   */
  public enum QuestionType {
    TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    RATING
  }
}
