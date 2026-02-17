package com.yourafterspace.yas_backend.service;

import com.yourafterspace.yas_backend.dto.CategoryDto;
import com.yourafterspace.yas_backend.dto.QuestionDto;
import com.yourafterspace.yas_backend.dto.QuestionDto.QuestionType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Provides questionnaire categories and questions. Each category has questions directly (no
 * subcategories). Frontend can show search for multi-choice when options count is large.
 */
@Service
public class QuestionServiceImpl implements QuestionService {

  private static final String CAT_BACKGROUND = "background";
  private static final String CAT_BACKGROUND_NAME = "Background";
  private static final String CAT_INTERESTS = "interests";
  private static final String CAT_INTERESTS_NAME = "Interests";
  private static final String CAT_MOVIES_AND_SHOWS = "movies_and_shows";
  private static final String CAT_MOVIES_AND_SHOWS_NAME = "Movies and Shows";
  private static final String CAT_PERSPECTIVES = "perspectives";
  private static final String CAT_PERSPECTIVES_NAME = "Perspectives";
  private static final String CAT_SELF = "self";
  private static final String CAT_SELF_NAME = "Self";
  private static final String CAT_SUBSTANCES = "substances";
  private static final String CAT_SUBSTANCES_NAME = "Substances";

  @Override
  public List<CategoryDto> getCategories() {
    return List.of(
        buildBackgroundCategory(),
        buildInterestsCategory(),
        buildMoviesAndShowsCategory(),
        buildPerspectivesCategory(),
        buildSelfCategory(),
        buildSubstancesCategory());
  }

  @Override
  public List<QuestionDto> getAllQuestions() {
    return getCategories().stream()
        .flatMap(c -> c.getQuestions() != null ? c.getQuestions().stream() : List.<QuestionDto>of().stream())
        .toList();
  }

  @Override
  public Optional<CategoryDto> getCategoryById(String categoryId) {
    return getCategories().stream()
        .filter(c -> categoryId != null && categoryId.equals(c.getId()))
        .findFirst();
  }

  private CategoryDto buildBackgroundCategory() {
    List<QuestionDto> questions =
        List.of(
            question(
                "fluent_languages",
                "Fluent languages",
                "What languages do you speak fluently?",
                QuestionType.MULTIPLE_CHOICE,
                List.of("Hindi", "Telugu", "English", "Kannada", "Tamil", "Malayalam"),
                CAT_BACKGROUND,
                CAT_BACKGROUND_NAME,
                1.0),
            question(
                "sibling_order",
                "Sibling order",
                "I am a ___ child",
                QuestionType.SINGLE_CHOICE,
                List.of("Younger", "Middle", "Older", "Only"),
                CAT_BACKGROUND,
                CAT_BACKGROUND_NAME,
                1.0),
            question(
                "home_town",
                "Home town",
                "What is your home town?",
                QuestionType.TEXT,
                null,
                CAT_BACKGROUND,
                CAT_BACKGROUND_NAME,
                1.0),
            question(
                "financial_status",
                "Financial status",
                "What financial status did you grow up in?",
                QuestionType.SINGLE_CHOICE,
                List.of("Working class", "Middle class", "Upper class", "Ultra wealthy"),
                CAT_BACKGROUND,
                CAT_BACKGROUND_NAME,
                1.0),
            question(
                "interesting_fact",
                "Interesting fact",
                "What is the fun or interesting fact about you that you want to share with your group?",
                QuestionType.TEXT,
                null,
                CAT_BACKGROUND,
                CAT_BACKGROUND_NAME,
                1.0));

    CategoryDto cat =
        new CategoryDto(
            CAT_BACKGROUND,
            CAT_BACKGROUND_NAME,
            "Your background and upbringing",
            questions);
    cat.setWeight(4.0); // Background has highest weight for overall profile completion
    cat.setImageUrl(
        "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=800&q=80");
    return cat;
  }

  private CategoryDto buildInterestsCategory() {
    List<QuestionDto> questions =
        List.of(
            question(
                "hobbies",
                "Hobbies",
                "What are your hobbies or activities you enjoy?",
                QuestionType.MULTIPLE_CHOICE,
                List.of(
                    "Reading", "Travel", "Music", "Sports", "Cooking", "Photography",
                    "Gardening", "Gaming", "Movies", "Art", "Dancing", "Trekking",
                    "Yoga", "Meditation", "Cycling", "Writing", "Volunteering", "Pets",
                    "Technology", "Fashion", "Fitness", "Running", "Swimming", "Painting",
                    "Podcasts", "Blogging", "Chess", "Board games", "Camping", "Fishing"),
                CAT_INTERESTS,
                CAT_INTERESTS_NAME,
                1.0),
            question(
                "my_interest",
                "My interest",
                "What are your main interests?",
                QuestionType.MULTIPLE_CHOICE,
                List.of(
                    "Fashion", "Online communities", "Teaching", "Online dating",
                    "Entrepreneurship", "Creative arts", "Social causes", "Fitness & wellness",
                    "Technology", "Travel & exploration", "Food & culinary", "Music & performing arts"),
                CAT_INTERESTS,
                CAT_INTERESTS_NAME,
                1.0),
            question(
                "nature_rating",
                "Time with nature",
                "I enjoy spending time with nature",
                QuestionType.RATING,
                List.of("1", "2", "3", "4", "5", "6", "7"),
                CAT_INTERESTS,
                CAT_INTERESTS_NAME,
                1.0),
            question(
                "enjoy_with_friends",
                "Time with friends",
                "I enjoy ___ with friends",
                QuestionType.MULTIPLE_CHOICE,
                List.of(
                    "Party", "Dinner", "Sports", "Movies", "Travel", "Gaming",
                    "Coffee chats", "Concerts", "Hiking", "Shopping", "Cooking together", "Road trips"),
                CAT_INTERESTS,
                CAT_INTERESTS_NAME,
                1.0),
            question(
                "sports_watch",
                "Sports I watch",
                "What sport do you watch?",
                QuestionType.MULTIPLE_CHOICE,
                List.of(
                    "Cricket", "Football", "Tennis", "Basketball", "Badminton",
                    "Formula 1", "Wrestling", "Kabaddi", "Hockey", "Volleyball", "Athletics"),
                CAT_INTERESTS,
                CAT_INTERESTS_NAME,
                1.0));

    CategoryDto cat =
        new CategoryDto(
            CAT_INTERESTS,
            CAT_INTERESTS_NAME,
            "Your interests and preferences",
            questions);
    cat.setWeight(1.0);
    cat.setImageUrl("https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=800&q=80");
    return cat;
  }

  private CategoryDto buildMoviesAndShowsCategory() {
    List<QuestionDto> questions =
        List.of(
            question(
                "favourite_movies",
                "Favourite movies",
                "What are your favourite movies?",
                QuestionType.MULTIPLE_CHOICE,
                List.of(
                    "3 Idiots", "Dangal", "Lagaan", "Sholay", "Dilwale Dulhania Le Jayenge",
                    "Taare Zameen Par", "PK", "Bajrangi Bhaijaan", "Bahubali", "KGF",
                    "Inception", "The Dark Knight", "Interstellar", "Forrest Gump", "The Shawshank Redemption",
                    "Pulp Fiction", "Fight Club", "The Godfather", "Titanic", "Avatar"),
                CAT_MOVIES_AND_SHOWS,
                CAT_MOVIES_AND_SHOWS_NAME,
                1.0),
            question(
                "favourite_shows",
                "Favourite shows",
                "What are your favourite TV shows or web series?",
                QuestionType.MULTIPLE_CHOICE,
                List.of(
                    "Sacred Games", "Delhi Crime", "Family Man", "Scam 1992", "Aspirants",
                    "Panchayat", "Kota Factory", "Gullak", "TVF Pitchers", "Permanent Roommates",
                    "Breaking Bad", "Game of Thrones", "Stranger Things", "Friends", "The Office",
                    "Sherlock", "Money Heist", "Squid Game", "Wednesday", "The Crown"),
                CAT_MOVIES_AND_SHOWS,
                CAT_MOVIES_AND_SHOWS_NAME,
                1.0));

    CategoryDto cat =
        new CategoryDto(
            CAT_MOVIES_AND_SHOWS,
            CAT_MOVIES_AND_SHOWS_NAME,
            "Your favourite movies and shows",
            questions);
    cat.setWeight(1.0);
    cat.setImageUrl("https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&q=80");
    return cat;
  }

  private CategoryDto buildPerspectivesCategory() {
    List<QuestionDto> questions =
        List.of(
            question(
                "innate_purpose",
                "Innate purpose",
                "I believe humans are born with an innate purpose",
                QuestionType.RATING,
                List.of("1", "2", "3", "4", "5", "6", "7"),
                CAT_PERSPECTIVES,
                CAT_PERSPECTIVES_NAME,
                1.0),
            question(
                "astrology_view",
                "View on astrology",
                "What do you think about astrology?",
                QuestionType.SINGLE_CHOICE,
                List.of(
                    "I don't believe in it",
                    "It's fake",
                    "Fun to talk about",
                    "I find it interesting",
                    "I believe in it"),
                CAT_PERSPECTIVES,
                CAT_PERSPECTIVES_NAME,
                1.0),
            question(
                "comedy_politically_correct",
                "Comedy and political correctness",
                "I believe comedy is becoming too politically correct",
                QuestionType.RATING,
                List.of("1", "2", "3", "4", "5", "6", "7"),
                CAT_PERSPECTIVES,
                CAT_PERSPECTIVES_NAME,
                1.0),
            question(
                "meet_other_political_side",
                "Meeting different political views",
                "Do you like meeting people of the other side of the political spectrum?",
                QuestionType.SINGLE_CHOICE,
                List.of(
                    "I prefer not to",
                    "I am different from them",
                    "I like to meet them",
                    "I enjoy hearing different views",
                    "It doesn't matter to me"),
                CAT_PERSPECTIVES,
                CAT_PERSPECTIVES_NAME,
                1.0));

    CategoryDto cat =
        new CategoryDto(
            CAT_PERSPECTIVES,
            CAT_PERSPECTIVES_NAME,
            "Your perspectives and beliefs",
            questions);
    cat.setWeight(1.0);
    cat.setImageUrl("https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80");
    return cat;
  }

  private CategoryDto buildSelfCategory() {
    List<QuestionDto> questions =
        List.of(
            question(
                "relationship_status",
                "Relationship status",
                "What is your relationship status?",
                QuestionType.SINGLE_CHOICE,
                List.of("Single", "Committed", "Married"),
                CAT_SELF,
                CAT_SELF_NAME,
                1.0),
            question(
                "self_attractiveness",
                "Self-perceived attractiveness",
                "How attractive do you consider yourself?",
                QuestionType.RATING,
                List.of("1", "2", "3", "4", "5", "6", "7"),
                CAT_SELF,
                CAT_SELF_NAME,
                1.0),
            question(
                "self_intelligence",
                "Self-perceived intelligence",
                "How intelligent do you consider yourself?",
                QuestionType.RATING,
                List.of("1", "2", "3", "4", "5", "6", "7"),
                CAT_SELF,
                CAT_SELF_NAME,
                1.0),
            question(
                "maths_smart",
                "Maths smart",
                "Do you consider yourself maths smart?",
                QuestionType.RATING,
                List.of("1", "2", "3", "4", "5", "6", "7"),
                CAT_SELF,
                CAT_SELF_NAME,
                1.0),
            question(
                "street_smart",
                "Street smart",
                "Do you consider yourself street smart?",
                QuestionType.SINGLE_CHOICE,
                List.of(
                    "I can be a bit naive",
                    "I can handle anything",
                    "Smart enough to avoid a ripoff"),
                CAT_SELF,
                CAT_SELF_NAME,
                1.0),
            question(
                "religious_affiliation",
                "Religious affiliation",
                "What religious affiliation do you identify with?",
                QuestionType.SINGLE_CHOICE,
                List.of(
                    "Hindu", "Muslim", "Christian", "Sikh", "Buddhist", "Jain",
                    "Jewish", "Atheist", "Agnostic", "Spiritual but not religious",
                    "Prefer not to say", "Other"),
                CAT_SELF,
                CAT_SELF_NAME,
                1.0),
            question(
                "instagram_id",
                "Instagram ID",
                "What is your Instagram ID?",
                QuestionType.TEXT,
                null,
                CAT_SELF,
                CAT_SELF_NAME,
                1.0));

    CategoryDto cat =
        new CategoryDto(
            CAT_SELF,
            CAT_SELF_NAME,
            "About yourself",
            questions);
    cat.setWeight(1.0);
    cat.setImageUrl("https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=800&q=80");
    return cat;
  }

  private CategoryDto buildSubstancesCategory() {
    List<String> ratingOptions = List.of("1", "2", "3", "4", "5", "6", "7");
    List<QuestionDto> questions =
        List.of(
            question(
                "vaping_with_friends",
                "Vaping with friends",
                "I enjoy vaping with friends",
                QuestionType.RATING,
                ratingOptions,
                CAT_SUBSTANCES,
                CAT_SUBSTANCES_NAME,
                1.0),
            question(
                "smoking_with_friends",
                "Smoking with friends",
                "I enjoy smoking with friends",
                QuestionType.RATING,
                ratingOptions,
                CAT_SUBSTANCES,
                CAT_SUBSTANCES_NAME,
                1.0),
            question(
                "psychedelics",
                "Using psychedelics",
                "I enjoy using psychedelics",
                QuestionType.RATING,
                ratingOptions,
                CAT_SUBSTANCES,
                CAT_SUBSTANCES_NAME,
                1.0),
            question(
                "drinking_with_friends",
                "Drinking with friends",
                "I enjoy drinking with friends",
                QuestionType.RATING,
                ratingOptions,
                CAT_SUBSTANCES,
                CAT_SUBSTANCES_NAME,
                1.0),
            question(
                "cocaine_with_friends",
                "Cocaine with friends",
                "I enjoy using cocaine with friends",
                QuestionType.RATING,
                ratingOptions,
                CAT_SUBSTANCES,
                CAT_SUBSTANCES_NAME,
                1.0));

    CategoryDto cat =
        new CategoryDto(
            CAT_SUBSTANCES,
            CAT_SUBSTANCES_NAME,
            "Substance use and preferences",
            questions);
    cat.setWeight(1.0);
    cat.setImageUrl("https://images.unsplash.com/photo-1514933651103-005c3ef206bb?w=800&q=80");
    return cat;
  }

  private QuestionDto question(
      String id,
      String title,
      String description,
      QuestionType type,
      List<String> options,
      String categoryId,
      String categoryName,
      double weight) {
    QuestionDto q = new QuestionDto();
    q.setId(id);
    q.setTitle(title);
    q.setDescription(description);
    q.setType(type);
    q.setOptions(options);
    q.setCategoryId(categoryId);
    q.setCategoryName(categoryName);
    q.setWeight(weight);
    return q;
  }
}
