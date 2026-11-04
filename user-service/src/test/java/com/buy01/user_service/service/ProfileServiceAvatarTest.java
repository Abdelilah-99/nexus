package com.buy01.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.buy01.user_service.client.MediaServiceClient;
import com.buy01.user_service.dto.external.MediaMetadataResponse;
import com.buy01.user_service.exception.ForbiddenException;
import com.buy01.user_service.model.Role;
import com.buy01.user_service.model.User;
import com.buy01.user_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceAvatarTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaServiceClient mediaServiceClient;

    @InjectMocks
    private ProfileService profileService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("u1");
        user.setUsername("alice");
        user.setEmail("a@b.com");
        user.setRole(Role.CLIENT);
    }

    @Test
    void updateProfile_withHttpAvatar_setsDirectly() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = profileService.updateProfile("u1", "alice", "https://cdn.example.com/a.png");

        assertThat(dto.getAvatarUrl()).isEqualTo("https://cdn.example.com/a.png");
    }

    @Test
    void updateProfile_withMediaId_resolvesUrl() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaServiceClient.fetchMetadata("m1"))
                .thenReturn(new MediaMetadataResponse("m1", "f", "image/png", "https://media/u", 1, "u1"));

        var dto = profileService.updateProfile("u1", "alice", "m1");

        assertThat(dto.getAvatarUrl()).isEqualTo("https://media/u");
    }

    @Test
    void updateProfile_mediaOwnedByOther_throwsUnauthorized() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(mediaServiceClient.fetchMetadata("m1"))
                .thenReturn(new MediaMetadataResponse("m1", "f", "image/png", "https://media/u", 1, "other"));

        assertThrows(ForbiddenException.class, () -> profileService.updateProfile("u1", "alice", "m1"));
    }

    @Test
    void updateProfile_blankAvatar_leavesAvatarUntouched() {
        user.setAvatarUrl("old");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = profileService.updateProfile("u1", "alice", "   ");

        assertThat(dto.getAvatarUrl()).isEqualTo("old");
    }

    @Test
    void updateProfile_userNotFound_throws() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(com.buy01.user_service.exception.UserNotFoundException.class,
                () -> profileService.updateProfile("missing", "alice", null));
    }
}