package com.buy01.user_service.service;

import org.springframework.stereotype.Service;

import com.buy01.user_service.client.MediaServiceClient;
import com.buy01.user_service.dto.UserDto;
import com.buy01.user_service.dto.external.MediaMetadataResponse;
import com.buy01.user_service.exception.ForbiddenException;
import com.buy01.user_service.exception.UserNotFoundException;
import com.buy01.user_service.model.User;
import com.buy01.user_service.repository.UserRepository;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final MediaServiceClient mediaServiceClient;

    public ProfileService(UserRepository userRepository, MediaServiceClient mediaServiceClient) {
        this.userRepository = userRepository;
        this.mediaServiceClient = mediaServiceClient;
    }

    public UserDto getProfile(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return toDto(user);
    }

    public UserDto updateProfile(
            String userId,
            String username,
            String avatarUrl
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setUsername(username);

        if (avatarUrl != null && !avatarUrl.isBlank()) {
            if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                user.setAvatarUrl(avatarUrl.trim());
            } else {
                user.setAvatarUrl(resolveAvatarUrl(avatarUrl.trim(), userId));
            }
        }

        User savedUser = userRepository.save(user);

        return toDto(savedUser);
    }

    private String resolveAvatarUrl(String mediaId, String userId) {
        MediaMetadataResponse media = mediaServiceClient.fetchMetadata(mediaId);
        if (!userId.equals(media.uploaderId())) {
            throw new ForbiddenException("You do not own media: " + mediaId);
        }
        return media.url();
    }

    private UserDto toDto(User user) {

        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getAvatarUrl()
        );
    }
}