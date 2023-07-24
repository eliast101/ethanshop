package com.ethanstore.api.service;

import com.ethanstore.api.domain.User;
import com.ethanstore.api.exception.domain.EmailExistException;
import com.ethanstore.api.exception.domain.EmailNotFoundException;
import com.ethanstore.api.exception.domain.UserNotFoundException;
import com.ethanstore.api.exception.domain.UsernameExistException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface UserService {

    List<User> getAllUsers();

    User findUserByUsername(String username);

    User findUserByEmail(String email);

    User register(String firstName, String lastName, String username, String email) throws UserNotFoundException, UsernameExistException, EmailExistException;

    User addNewUser(String firstName, String lastName, String username, String email, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException;

    User updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNonLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException;

    void deleteUser(Long id);

    void resetPassword(String email) throws EmailNotFoundException;

    User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException;
}
