package com.yourafterspace.yas_backend.controller;

import com.yourafterspace.yas_backend.dto.ApiResponse;
import com.yourafterspace.yas_backend.dto.CategoryDto;
import com.yourafterspace.yas_backend.service.QuestionService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for questionnaire categories and questions. Public so the frontend can load questions
 * before login. Response is category-wise with subcategories and questions; each question has
 * title, description, type (TEXT, SINGLE_CHOICE, MULTIPLE_CHOICE, MULTIPLE_CHOICE_SEARCH, RATING).
 */
@RestController
@RequestMapping("/v1")
public class QuestionController {

  private static final Logger logger = LoggerFactory.getLogger(QuestionController.class);

  private final QuestionService questionService;

  public QuestionController(QuestionService questionService) {
    this.questionService = questionService;
  }

  /**
   * Get all questionnaire categories with subcategories and questions. Use question {@code id} when
   * submitting answers (POST /v1/user/questionnaire). Each question has title, description, type
   * (for display), and options when applicable.
   *
   * @return List of categories (e.g. Background, Interests), each with subcategories and
   *     questions
   */
  @GetMapping("/questions")
  public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategories() {
    logger.debug("Fetching questionnaire categories and questions");

    List<CategoryDto> categories = questionService.getCategories();
    return ResponseEntity.ok(
        ApiResponse.success("Categories and questions retrieved successfully", categories));
  }
}
