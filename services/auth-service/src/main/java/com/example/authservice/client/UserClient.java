package com.example.authservice.client;

import com.example.authservice.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String userServiceUrl;

    public UserClient(@Value("${services.user-url}") String userServiceUrl) {
        this.userServiceUrl = userServiceUrl;
    }

    public UserResponse getUserByEmail(String email) {
        try {
            return restTemplate.getForObject(
                    userServiceUrl + "/users/by-email?email={email}",
                    UserResponse.class,
                    email);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
