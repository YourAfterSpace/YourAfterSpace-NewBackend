package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.CategoryDto;
import com.yourafterspace.yas_backend.dto.QuestionDto;
import java.util.List;
import java.util.Optional;

/** Service that provides questionnaire categories and questions. */
public interface QuestionService {

  /**
   * Returns all categories with subcategories and questions. Use for GET /v1/questions and for
   * computing progress.
   *
   * @return List of categories (e.g. Background, Interests), each with subcategories and questions
   */
  List<CategoryDto> getCategories();

  /**
   * Returns a single category by id with its questions, or empty if not found.
   *
   * @param categoryId category id (e.g. "background", "interests")
   * @return Optional of the category
   */
  Optional<CategoryDto> getCategoryById(String categoryId);

  /**
   * Returns all questions flattened (legacy/convenience). Prefer getCategories() for frontend.
   *
   * @return List of all questions with category/subcategory info
   */
  List<QuestionDto> getAllQuestions();
}
