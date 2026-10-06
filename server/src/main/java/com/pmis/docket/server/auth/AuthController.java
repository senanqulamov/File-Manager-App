package com.pmis.docket.server.auth;

import com.pmis.docket.server.model.User;
import com.pmis.docket.server.web.Dto.LoginRequest;
import com.pmis.docket.server.web.Dto.LoginResponse;
import com.pmis.docket.server.web.Dto.PasswordRequest;
import com.pmis.docket.server.web.Dto.UserDto;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "app", "PMIS Docket Server", "version", "1.0.0");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        AuthService.SignIn s = auth.login(req.login(), req.password(), req.computer());
        return new LoginResponse(s.token(), toDto(s.user()));
    }

    @GetMapping("/auth/me")
    public UserDto me(@RequestAttribute(AuthInterceptor.USER_ATTR) User user) {
        return toDto(user);
    }

    @PostMapping("/auth/password")
    public UserDto changePassword(@RequestAttribute(AuthInterceptor.USER_ATTR) User user,
                                  @RequestAttribute(value = AuthInterceptor.COMPUTER_ATTR, required = false) String computer,
                                  @RequestBody PasswordRequest req) {
        return toDto(auth.changePassword(user, computer, req.oldPassword(), req.newPassword()));
    }

    @PostMapping("/auth/logout")
    public void logout(@RequestAttribute(AuthInterceptor.USER_ATTR) User user,
                       @RequestAttribute(AuthInterceptor.TOKEN_ATTR) String token,
                       @RequestAttribute(value = AuthInterceptor.COMPUTER_ATTR, required = false) String computer) {
        auth.logout(token, user, computer);
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.id, u.login, u.displayName, u.initials(), u.department, u.role.name(), u.mustChangePassword);
    }
}
