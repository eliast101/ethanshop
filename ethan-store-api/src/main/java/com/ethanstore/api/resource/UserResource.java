package com.ethanstore.api.resource;

import com.ethanstore.api.domain.HttpResponse;
import com.ethanstore.api.domain.User;
import com.ethanstore.api.domain.UserPrincipal;
import com.ethanstore.api.exception.domain.EmailExistException;
import com.ethanstore.api.exception.domain.EmailNotFoundException;
import com.ethanstore.api.exception.domain.UserNotFoundException;
import com.ethanstore.api.exception.domain.UsernameExistException;
import com.ethanstore.api.exception.handler.ResourceExceptionHandler;
import com.ethanstore.api.service.UserService;
import com.ethanstore.api.util.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.ethanstore.api.constant.FileConstant.*;
import static com.ethanstore.api.constant.SecurityConstant.JWT_TOKEN_HEADER;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;

@RestController
@RequestMapping(path = { "/", "/user" })
public class UserResource extends ResourceExceptionHandler {

    private static final String EMAIL_SENT = "Password reset successful. New password sent to email: ";
    private static final String USER_DELETED_SUCCESSFULLY = "User deleted successfully";

    private UserService userService;

    private AuthenticationManager authenticationManager;

    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    public UserResource(UserService userService, AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody User user) throws UserNotFoundException, UsernameExistException, EmailExistException {
        User newUser = userService.register(user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail());
        return ResponseEntity.ok(newUser);
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody User user) {
        authenticateUser(user.getUsername(), user.getPassword());
        User loginUser = userService.findUserByUsername(user.getUsername());
        UserPrincipal userPrincipal = new UserPrincipal(loginUser);
        HttpHeaders jwtHeaders = getJwtHeader(userPrincipal);

        return ResponseEntity.ok().headers(jwtHeaders).body(loginUser);
    }

    @PostMapping("/add")
    public ResponseEntity<User> addUser(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("role") String role,
            @RequestParam("isActive") String isActive,
            @RequestParam("isNonLocked") String isNonLocked,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage
    ) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException {
        User newUser = userService.addNewUser(firstName, lastName, username, email, role, Boolean.parseBoolean(isNonLocked), Boolean.parseBoolean(isActive), profileImage);
        return new ResponseEntity<>(newUser, CREATED);
    }

    @PostMapping("/update")
    public ResponseEntity<User> updateUser(
            @RequestParam("currentUsername") String currentUsername,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("role") String role,
            @RequestParam("isActive") String isActive,
            @RequestParam("isNonLocked") String isNonLocked,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage
    ) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException {
        User updatedUser = userService.updateUser(currentUsername, firstName, lastName, username, email, role, Boolean.parseBoolean(isNonLocked), Boolean.parseBoolean(isActive), profileImage);
        return ResponseEntity.ok(updatedUser);
    }

    @GetMapping("/find/{username}")
    public ResponseEntity<User> findUser(@PathVariable String username) {
        User user = userService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/list")
    public ResponseEntity<List<User>> findAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/reset-password/{email}")
    public ResponseEntity<HttpResponse> resetPassword(@PathVariable String email) throws EmailNotFoundException {
        userService.resetPassword(email);
        return response(OK, EMAIL_SENT + email);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ResponseEntity<HttpResponse> deleteUser(@PathVariable String id) {
        userService.deleteUser(Long.parseLong(id));
        return response(OK, USER_DELETED_SUCCESSFULLY);
    }

    @PostMapping("/update-profile-image")
    public ResponseEntity<User> updateProfileImage(
            @RequestParam("username") String username,
            @RequestParam(value = "profileImage") MultipartFile profileImage
    ) throws UserNotFoundException, UsernameExistException, EmailExistException, IOException {
        User user = userService.updateProfileImage(username, profileImage);
        return ResponseEntity.ok(user);
    }

    @GetMapping(value = "/image/{username}/{fileName}", produces = IMAGE_JPEG_VALUE)
    public byte[] getProfileImage(@PathVariable String username, @PathVariable String fileName) throws IOException {
        return Files.readAllBytes(Paths.get(USER_FOLDER + username + FORWARD_SLASH + fileName));
    }

    @GetMapping(value = "/image/profile/{firstLastName}", produces = IMAGE_JPEG_VALUE)
    public byte[] getTempProfileImage(@PathVariable String firstLastName) throws IOException {
        URL url = new URL(TMP_PROFILE_IMAGE_BASE_URL + firstLastName);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = url.openStream()) {
            int bytesRead;
            byte[] chunk = new byte[1024];
            while ((bytesRead = inputStream.read(chunk)) > 0) {
                byteArrayOutputStream.write(chunk, 0, bytesRead);
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private ResponseEntity<HttpResponse> response(HttpStatus httpStatus, String message) {
        HttpResponse httpResponse = HttpResponse.builder()
                .httpStatusCode(httpStatus.value())
                .httpStatus(httpStatus)
                .reason(httpStatus.getReasonPhrase().toUpperCase())
                .message(message)
                .build();
        return ResponseEntity.status(httpStatus).body(httpResponse);
    }

    private HttpHeaders getJwtHeader(UserPrincipal userPrincipal) {
        String token = jwtTokenProvider.generateJwttoken(userPrincipal);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JWT_TOKEN_HEADER, token);

        return httpHeaders;
    }

    private void authenticateUser(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

}
