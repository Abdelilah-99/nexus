package com.neo4flix.user.web;

import java.util.List;
import com.neo4flix.security.CurrentUser;
import com.neo4flix.user.repository.UserRepository;
import com.neo4flix.user.service.ProfileService;
import com.neo4flix.user.web.ProfileDtos.ChangePasswordRequest;
import com.neo4flix.user.web.ProfileDtos.DeleteAccountRequest;
import com.neo4flix.user.web.ProfileDtos.UpdateProfileRequest;
import com.neo4flix.user.web.UserDto.PublicUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final ProfileService profiles;
    private final UserRepository users;

    public UserController(ProfileService profiles, UserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    @GetMapping("/api/users/me")
    public UserDto me() {
        return UserDto.of(profiles.get(CurrentUser.id()));
    }

    @PutMapping("/api/users/me")
    public UserDto update(@Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(CurrentUser.id(), request);
    }

    @PutMapping("/api/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(CurrentUser.id(), request);
    }

    /** A password is required so a stolen token alone cannot delete the account. */
    @DeleteMapping("/api/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@Valid @RequestBody DeleteAccountRequest request) {
        profiles.delete(CurrentUser.id(), request.password());
    }

    @GetMapping("/api/users/{id}")
    public PublicUser publicProfile(@PathVariable String id) {
        return PublicUser.of(profiles.get(id));
    }

    @GetMapping("/api/admin/users")
    public List<UserDto> all() {
        return users.findAll().stream().map(UserDto::of).toList();
    }

    @DeleteMapping("/api/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        profiles.deleteByAdmin(id);
    }
}
