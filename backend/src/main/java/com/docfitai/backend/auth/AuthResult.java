package com.docfitai.backend.auth;

import com.docfitai.backend.auth.dto.UserDto;

/** Internal result of an auth operation -- the raw refresh token is set as a cookie and never returned in a response body. */
record AuthResult(String accessToken, String rawRefreshToken, UserDto user) {
}
