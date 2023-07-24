package com.ethanstore.api.service.impl;

import com.ethanstore.api.domain.User;
import com.ethanstore.api.domain.UserPrincipal;
import com.ethanstore.api.enumeration.Role;
import com.ethanstore.api.exception.domain.EmailExistException;
import com.ethanstore.api.exception.domain.EmailNotFoundException;
import com.ethanstore.api.exception.domain.UserNotFoundException;
import com.ethanstore.api.exception.domain.UsernameExistException;
import com.ethanstore.api.repository.UserRepository;
import com.ethanstore.api.service.LoginAttemptService;
import com.ethanstore.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.ethanstore.api.constant.FileConstant.*;
import static com.ethanstore.api.constant.UserImplConstant.*;
import static com.ethanstore.api.enumeration.Role.ROLE_USER;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Service
@Transactional
@Qualifier("userDetailsService")
@Slf4j
public class UserServiceImpl implements UserService, UserDetailsService {

    private UserRepository userRepository;

    private BCryptPasswordEncoder passwordEncoder;

    private LoginAttemptService loginAttemptService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            log.error("user not found by username: {}", username);
            throw new UsernameNotFoundException("User not found by username: " + username);
        }
        validateLoginAttempt(user);
        user.setLastLoginDateDisplay(user.getLastLoginDate());
        user.setLastLoginDate(LocalDateTime.now());
        userRepository.save(user);
        log.info("Returning found user by username: {}", username);

        return new UserPrincipal(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User register(String firstName, String lastName, String username, String email) throws UserNotFoundException, UsernameExistException, EmailExistException {
        validateUsernameAndEmail(EMPTY, username, email);
        String password = generatePassword();
        log.info("Register: New User Password: {}", password);
        User user = User.builder()
                .userId(generateUserId())
                .password(encryptPassword(password))
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .email(email)
                .isActive(true)
                .isNotLocked(true)
                .joinDate(LocalDateTime.now())
                .profileImageUrl(getDefaultUserProfileImageUri(firstName + "+" + lastName))
                .role(ROLE_USER.name())
                .authorities(ROLE_USER.getAuthorities())
                .build();
        userRepository.save(user);

        return user;
    }

    @Override
    public User addNewUser(String firstName, String lastName, String username, String email, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException {
        validateUsernameAndEmail(EMPTY, username, email);
        String password = generatePassword();
        User user = User.builder()
                .userId(generateUserId())
                .password(encryptPassword(password))
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .email(email)
                .isActive(isActive)
                .isNotLocked(isNonLocked)
                .joinDate(LocalDateTime.now())
                .profileImageUrl(getDefaultUserProfileImageUri(firstName + "+" + lastName))
                .role(getRoleEnumName(role).name())
                .authorities(getRoleEnumName(role).getAuthorities())
                .build();
        userRepository.save(user);
        saveProfileImage(user, profileImage);
        log.info("Add User: New User Password: {}", password);
        return user;
    }

    @Override
    public User updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException {
        User currentUser = validateUsernameAndEmail(currentUsername, newUsername, newEmail);

        User updatedUser = Objects.requireNonNull(currentUser).toBuilder()
                .firstName(newFirstName)
                .lastName(newLastName)
                .username(newUsername)
                .email(newEmail)
                .isActive(isActive)
                .isNotLocked(isNonLocked)
                .role(getRoleEnumName(role).name())
                .authorities(getRoleEnumName(role).getAuthorities())
                .build();
        userRepository.save(updatedUser);
        saveProfileImage(updatedUser, profileImage);

        return updatedUser;
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public void resetPassword(String email) throws EmailNotFoundException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new EmailNotFoundException(NO_USER_FOUND_BY_EMAIL + email);
        }
        String password = generatePassword();
        user.setPassword(encryptPassword(password));
        userRepository.save(user);
        log.info("Reset Password: New User Password: {}", password);
        // send the plain password in email to user
    }

    @Override
    public User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException {
        User currentUser = validateUsernameAndEmail(username, null, null);
        saveProfileImage(currentUser, profileImage);

        return currentUser;
    }

    private void saveProfileImage(User user, MultipartFile profileImage) throws IOException {
        if (profileImage != null) {
            Path userFolder = Paths.get(USER_FOLDER + user.getUsername()).toAbsolutePath().normalize();
            if (!Files.exists(userFolder)) {
                Files.createDirectories(userFolder);
                log.info(DIRECTORY_CREATED + userFolder);
            }
            Files.deleteIfExists(Paths.get(userFolder + user.getUsername() + DOT + JPG_EXTENTION));
            Files.copy(profileImage.getInputStream(), userFolder.resolve(user.getUsername() + DOT + JPG_EXTENTION), REPLACE_EXISTING);
            user.setProfileImageUrl(setProfileImageUrl(user.getUsername()));
            userRepository.save(user);
            log.info(FILE_SAVED_IN_FILE_SYSTEM + profileImage.getOriginalFilename());
        }
    }

    private String setProfileImageUrl(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(USER_IMAGE_PATH + username + FORWARD_SLASH + username + DOT + JPG_EXTENTION).toUriString();
    }

    private Role getRoleEnumName(String role) {
        return Role.valueOf(role.toUpperCase());
    }


    private void validateLoginAttempt(User user) {
        if (user.isNotLocked()) {
            if (loginAttemptService.hasExceededMaxAttempts(user.getUsername())) {
                user.setNotLocked(false);
            } else {
                user.setNotLocked(true);
            }
        } else {
            loginAttemptService.evictUserFromLoginAttemptCache(user.getUsername());
        }
    }

    private String getDefaultUserProfileImageUri(String firstLastName) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_USER_IMAGE_PATH + firstLastName).toUriString();
    }

    private String encryptPassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String generatePassword() {
        return RandomStringUtils.randomAlphanumeric(10);
    }

    private String generateUserId() {
        return RandomStringUtils.randomNumeric(10);
    }

    private User validateUsernameAndEmail(String currentUsername, String newUsername, String newEmail) throws UserNotFoundException, UsernameExistException, EmailExistException {
        User userByNewUsername = findUserByUsername(newUsername);
        User userByNewEmail = findUserByEmail(newEmail);
        if (StringUtils.isNotBlank(currentUsername)) {
            User currentUser = findUserByUsername(currentUsername);
            if (currentUser == null) {
                throw new UserNotFoundException(NO_USER_FOUND_BY_USERNAME + currentUsername);
            }
            if (userByNewUsername != null && !currentUser.getId().equals(userByNewUsername.getId())) {
                throw new UsernameExistException(ALREADY_EXISTS_BY_USERNAME + newUsername);
            }
            if (userByNewEmail != null && !currentUser.getEmail().equals(userByNewEmail.getEmail())) {
                throw new EmailExistException(ALREADY_EXISTS_BY_EMAIL + newEmail);
            }
            return currentUser;
        } else {
            if (userByNewUsername != null) {
                throw new UsernameExistException(ALREADY_EXISTS_BY_USERNAME + newUsername);
            }
            if (userByNewEmail != null) {
                throw new EmailExistException(ALREADY_EXISTS_BY_EMAIL + newEmail);
            }
        }
        return null;
    }
}
