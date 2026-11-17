package com.example.servicemanagement.service;

import com.example.servicemanagement.dto.request.CreateUserRequest;
import com.example.servicemanagement.dto.response.UserResponse;
import com.example.servicemanagement.entity.User;
import com.example.servicemanagement.exception.DuplicateEmailException;
import com.example.servicemanagement.exception.ResourceNotFoundException;
import com.example.servicemanagement.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmail(email)) {
            log.warn("Duplicate user email attempted: {}", email);
            throw new DuplicateEmailException(email);
        }

        User user = new User(request.name().trim(), email);
        User saved = userRepository.save(user);
        log.info("Created user with id {} and email {}", saved.getId(), saved.getEmail());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
