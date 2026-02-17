package com.yourafterspace.yas_backend.dto;

/** Per-category completion: answered count, total questions, percentage. */
public class CategoryProgressDto {

  private String categoryId;
  private String categoryName;
  private int answeredCount;
  private int totalCount;
  private double percentage;

  public CategoryProgressDto() {}

  public CategoryProgressDto(
      String categoryId, String categoryName, int answeredCount, int totalCount, double percentage) {
    this.categoryId = categoryId;
    this.categoryName = categoryName;
    this.answeredCount = answeredCount;
    this.totalCount = totalCount;
    this.percentage = percentage;
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

  public int getAnsweredCount() {
    return answeredCount;
  }

  public void setAnsweredCount(int answeredCount) {
    this.answeredCount = answeredCount;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }

  public double getPercentage() {
    return percentage;
  }

  public void setPercentage(double percentage) {
    this.percentage = percentage;
  }
}
