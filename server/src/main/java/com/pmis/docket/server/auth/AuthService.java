package com.pmis.docket.server.auth;

import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.model.SessionToken;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.repo.SessionRepository;
import com.pmis.docket.server.repo.UserRepository;
import com.pmis.docket.server.service.AuditService;
import com.pmis.docket.server.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository users;
    private final SessionRepository sessions;
    private final AuditService audit;
    private final DocketProperties props;

    public AuthService(UserRepository users, SessionRepository sessions, AuditService audit, DocketProperties props) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.props = props;
    }

    public String hash(String rawPassword) { return encoder.encode(rawPassword); }

    /** Accepts "a.karimova", "PMIS\a.karimova" or "a.karimova@pmis.local". */
    public static String normalizeLogin(String login) {
        if (login == null) return "";
        String l = login.trim();
        int slash = l.lastIndexOf('\\');
        if (slash >= 0) l = l.substring(slash + 1);
        int at = l.indexOf('@');
        if (at > 0) l = l.substring(0, at);
        return l.toLowerCase();
    }

    @Transactional
    public SignIn login(String login, String password, String computer) {
        String l = normalizeLogin(login);
        User user = users.findByLoginIgnoreCase(l).orElse(null);
        if (user == null || password == null || !encoder.matches(password, user.passwordHash)) {
            audit.record(user, computer, "Failed sign-in for “" + l + "”", "Sign-in", null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        }
        if (!user.active) {
            audit.record(user, computer, "Failed sign-in (account disabled)", "Sign-in", null);
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is disabled. Contact IT.");
        }
        sessions.deleteByExpiresAtBefore(Instant.now());
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        SessionToken s = new SessionToken();
        s.token = HexFormat.of().formatHex(bytes);
        s.userId = user.id;
        s.computer = computer == null ? "" : computer.trim();
        s.expiresAt = Instant.now().plus(props.getSessionHours(), ChronoUnit.HOURS);
        sessions.save(s);
        user.lastSignIn = Instant.now();
        user.lastComputer = s.computer;
        users.save(user);
        audit.record(user, s.computer, "Signed in", "Sign-in", null);
        return new SignIn(s.token, user);
    }

    @Transactional
    public User changePassword(User user, String computer, String oldPassword, String newPassword) {
        User u = users.findById(user.id).orElseThrow();
        if (oldPassword == null || !encoder.matches(oldPassword, u.passwordHash)) throw ApiException.badRequest("Your current password is not correct.");
        if (newPassword == null || newPassword.length() < 8) throw ApiException.badRequest("Use at least 8 characters for the new password.");
        if (newPassword.equals(oldPassword)) throw ApiException.badRequest("Choose a password different from the old one.");
        u.passwordHash = hash(newPassword);
        u.mustChangePassword = false;
        users.save(u);
        audit.record(u, computer, "Changed own password", "Sign-in", null);
        return u;
    }

    @Transactional
    public void logout(String token, User user, String computer) {
        sessions.deleteById(token);
        audit.record(user, computer, "Signed out", "Sign-in", null);
    }

    public record SignIn(String token, User user) { }
}
