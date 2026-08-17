package com.docfitai.backend.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.docfitai.backend.auth.dto.AuthResponseDto;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

public final class AuthTestHelper {

    private AuthTestHelper() {
    }

    public static String registerAndGetAccessToken(MockMvc mockMvc, ObjectMapper objectMapper, String email, String password)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AuthResponseDto.class).accessToken();
    }
}
