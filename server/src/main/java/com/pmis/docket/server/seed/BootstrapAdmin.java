package com.pmis.docket.server.seed;

import com.pmis.docket.server.auth.AuthService;
import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.model.Role;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.repo.UserRepository;
import com.pmis.docket.server.service.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On a real server with an empty database: creates the first IT administrator from
 * DOCKET_BOOTSTRAP_ADMIN_LOGIN / DOCKET_BOOTSTRAP_ADMIN_PASSWORD. They must change the password at first sign-in.
 */
@Component
@Order(1)
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private final DocketProperties props;
    private final UserRepository users;
    private final AuthService auth;
    private final NodeService nodes;

    public BootstrapAdmin(DocketProperties props, UserRepository users, AuthService auth, NodeService nodes) {
        this.props = props;
        this.users = users;
        this.auth = auth;
        this.nodes = nodes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.isSeedDemoData() || users.count() > 0) return;
        String login = AuthService.normalizeLogin(props.getBootstrapAdminLogin());
        String pwd = props.getBootstrapAdminPassword();
        if (login.isBlank() || pwd == null || pwd.isBlank()) {
            log.warn("No users exist yet. Set DOCKET_BOOTSTRAP_ADMIN_LOGIN and DOCKET_BOOTSTRAP_ADMIN_PASSWORD and restart to create the first IT administrator.");
            return;
        }
        User u = new User();
        u.login = login;
        u.displayName = "IT Administrator";
        u.department = "IT";
        u.groupKey = "it";
        u.role = Role.ADMIN;
        u.quotaBytes = 50L * 1024 * 1024 * 1024;
        u.passwordHash = auth.hash(pwd);
        u.mustChangePassword = true;
        users.save(u);
        nodes.personalRootOf(u);
        nodes.companyRoot();
        log.info("Created the first IT administrator “{}”. Sign in with Docket and change the password.", login);
    }
}
