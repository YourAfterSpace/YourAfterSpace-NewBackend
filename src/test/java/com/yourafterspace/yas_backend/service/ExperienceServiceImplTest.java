package com.yourafterspace.yas_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.yourafterspace.yas_backend.dao.ExperienceDao;
import com.yourafterspace.yas_backend.dao.UserExperienceDao;
import com.yourafterspace.yas_backend.dto.ExperienceResponse;
import com.yourafterspace.yas_backend.model.Experience;
import com.yourafterspace.yas_backend.model.UserExperience;
import com.yourafterspace.yas_backend.model.UserExperience.UserExperienceStatus;
import com.yourafterspace.yas_backend.repository.ExperienceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperienceServiceImplTest {

  @Mock private ExperienceRepository experienceRepository;
  @Mock private ExperienceDao experienceDao;
  @Mock private UserExperienceDao userExperienceDao;

  private ExperienceServiceImpl experienceService;

  @BeforeEach
  void setUp() {
    experienceService =
        new ExperienceServiceImpl(experienceRepository, experienceDao, userExperienceDao);
  }

  @Test
  void getUpcomingPaidExperiences_ReturnsFuturePaidExperiences() {
    String userId = "user-123";
    String expId = "exp-1";

    // Mock user experience
    UserExperience userExp = new UserExperience();
    userExp.setUserId(userId);
    userExp.setExperienceId(expId);
    userExp.setStatus(UserExperienceStatus.PAID);

    when(userExperienceDao.findByUserIdAndStatus(userId, UserExperienceStatus.PAID))
        .thenReturn(List.of(userExp));

    // Mock experience
    Experience experience = new Experience();
    experience.setExperienceId(expId);
    experience.setExperienceDate(LocalDate.now().plusDays(1)); // Future
    experience.setStartTime(LocalTime.of(10, 0));

    when(experienceDao.findByExperienceId(expId)).thenReturn(Optional.of(experience));

    // Execute
    List<ExperienceResponse> result = experienceService.getUpcomingPaidExperiences(userId);

    // Verify
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getExperienceId()).isEqualTo(expId);
  }

  @Test
  void getUpcomingPaidExperiences_FiltersPastExperiences() {
    String userId = "user-123";
    String expId = "exp-past";

    // Mock user experience
    UserExperience userExp = new UserExperience();
    userExp.setUserId(userId);
    userExp.setExperienceId(expId);
    userExp.setStatus(UserExperienceStatus.PAID);

    when(userExperienceDao.findByUserIdAndStatus(userId, UserExperienceStatus.PAID))
        .thenReturn(List.of(userExp));

    // Mock experience
    Experience experience = new Experience();
    experience.setExperienceId(expId);
    experience.setExperienceDate(LocalDate.now().minusDays(1)); // Past
    experience.setStartTime(LocalTime.of(10, 0));

    when(experienceDao.findByExperienceId(expId)).thenReturn(Optional.of(experience));

    // Execute
    List<ExperienceResponse> result = experienceService.getUpcomingPaidExperiences(userId);

    // Verify
    assertThat(result).isEmpty();
  }
}
