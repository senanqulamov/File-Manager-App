package com.pmis.docket.server.auth;

import com.pmis.docket.server.model.SessionToken;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.repo.SessionRepository;
import com.pmis.docket.server.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/** Checks the "Authorization: Bearer <token>" header on every /api call. */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_ATTR = "docketUser";
    public static final String COMPUTER_ATTR = "docketComputer";
    public static final String TOKEN_ATTR = "docketToken";

    private final SessionRepository sessions;
    private final UserRepository users;

    public AuthInterceptor(SessionRepository sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return deny(response, "Please sign in.");
        }
        String token = header.substring(7).trim();
        Optional<SessionToken> session = sessions.findById(token);
        if (session.isEmpty() || session.get().expiresAt.isBefore(Instant.now())) {
            return deny(response, "Your session has ended. Please sign in again.");
        }
        Optional<User> user = users.findById(session.get().userId);
        if (user.isEmpty() || !user.get().active) {
            return deny(response, "Your account is disabled. Contact IT.");
        }
        request.setAttribute(USER_ATTR, user.get());
        request.setAttribute(COMPUTER_ATTR, session.get().computer);
        request.setAttribute(TOKEN_ATTR, token);
        return true;
    }

    private boolean deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\"}");
        return false;
    }
}
