package com.pmis.docket.server.seed;

import com.pmis.docket.server.auth.AuthService;
import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.model.*;
import com.pmis.docket.server.repo.AccessRequestRepository;
import com.pmis.docket.server.repo.AclRepository;
import com.pmis.docket.server.repo.NodeRepository;
import com.pmis.docket.server.repo.ShareRepository;
import com.pmis.docket.server.repo.UserRepository;
import com.pmis.docket.server.service.NodeService;
import com.pmis.docket.server.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Creates demo users, company folders with permissions, and sample files.
 * Runs only when docket.seed-demo-data=true (the "local" profile) and the database is empty.
 * All demo users have the password: Docket2026!
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "Docket2026!";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DocketProperties props;
    private final UserRepository users;
    private final NodeRepository nodes;
    private final AclRepository acls;
    private final StorageService storage;
    private final NodeService nodeService;
    private final AuthService auth;
    private final ShareRepository shares;
    private final AccessRequestRepository requests;
    private Instant clock = Instant.now().minus(30, ChronoUnit.DAYS);

    public DemoDataSeeder(DocketProperties props, UserRepository users, NodeRepository nodes, AclRepository acls,
                          StorageService storage, NodeService nodeService, AuthService auth,
                          ShareRepository shares, AccessRequestRepository requests) {
        this.props = props;
        this.users = users;
        this.nodes = nodes;
        this.acls = acls;
        this.storage = storage;
        this.nodeService = nodeService;
        this.auth = auth;
        this.shares = shares;
        this.requests = requests;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.isSeedDemoData() || users.count() > 0) return;
        log.info("Seeding PMIS Docket demo data (password for all demo users: {})", DEMO_PASSWORD);

        User aylin = user("a.karimova", "Aylin Karimova", "Finance", "finance", Role.USER, true);
        User tural = user("t.mammadli", "Tural Mammadli", "Management", "management", Role.USER, true);
        User leyla = user("l.rahimli", "Leyla Rahimli", "HR", "hr", Role.USER, true);
        User sabina = user("s.novruzova", "Sabina Novruzova", "Operations", "operations", Role.USER, true);
        user("k.huseynov", "Kamran Huseynov", "Legal", "legal", Role.USER, false);
        User rashad = user("r.aliyev", "Rashad Aliyev", "IT", "it", Role.ADMIN, true);

        // ---------- Aylin's private folder ----------
        Node mine = nodeService.personalRootOf(aylin);
        Node contracts = folder(mine, "Contracts", aylin);
        Node lease = file(contracts, "Lease agreement 2026", "pdf", aylin, SampleFiles.pdf("Commercial lease agreement 2026",
                "Between PMIS (tenant) and the landlord.", "Term: 1 January 2026 - 31 December 2026.", "Monthly rent and service charges as per Schedule A."));
        file(contracts, "Supplier NDA - Caspian Logistics", "pdf", aylin, SampleFiles.pdf("Non-disclosure agreement",
                "Parties: PMIS and Caspian Logistics.", "Confidential information must not be shared with third parties."));
        Node invoices = folder(mine, "Invoices", aylin);
        file(invoices, "Invoice INV-0412", "pdf", aylin, SampleFiles.pdf("Invoice INV-0412", "Office supplies - September", "Total: 1,240.00 AZN", "Status: PAID"));
        file(invoices, "Expenses September", "csv", aylin, SampleFiles.text("Date,Description,Amount AZN",
                "2026-09-03,Taxi to client,18.50", "2026-09-11,Printer toner,96.00", "2026-09-24,Team lunch,212.40"));
        Node notes = folder(mine, "Notes", aylin);
        file(notes, "To-do this week", "txt", aylin, SampleFiles.text("To-do - week 41", "",
                "1. Send Q3 forecast to Tural", "2. Renew office lease", "3. Ask IT for access to Legal templates", "4. Archive September invoices"));
        file(notes, "Meeting ideas", "md", aylin, SampleFiles.text("# Ideas for Monday meeting", "",
                "- Move all contracts into Docket", "- One owner per folder", "- **Stop emailing attachments** - share instead"));
        Node photos = folder(mine, "Photos and video", aylin);
        file(photos, "Office party 01", "png", aylin, SampleFiles.png(1200, 800, new Color(0xF6B26B), new Color(0x8E7CC3), "Office party"));
        file(photos, "Company logo", "png", aylin, SampleFiles.png(512, 512, new Color(0x3B5BFF), new Color(0x6F86FF), "PMIS"));
        Node downloads = folder(mine, "Downloads", aylin);
        file(downloads, "Printer drivers", "zip", aylin, SampleFiles.zip(new java.util.LinkedHashMap<>(java.util.Map.of(
                "README.txt", "Install the printer driver from the setup folder.\r\nAsk IT if Windows blocks it.",
                "settings/printer.json", "{ \"model\": \"LaserJet M404\", \"duplex\": true }"))));
        file(notes, "Voice memo 2026-10-02", "wav", aylin, SampleFiles.wav(2.4));
        file(downloads, "export_2026-09", "json", aylin, SampleFiles.text("{", "  \"exported\": \"2026-09-19T09:00:00Z\",",
                "  \"invoices\": 42,", "  \"currency\": \"AZN\",", "  \"status\": \"complete\"", "}"));
        folder(mine, "Personal", aylin);
        file(mine, "Budget draft", "csv", aylin, SampleFiles.text("Item,Q4 AZN", "Salaries,184000", "Rent,36000", "Software,12500"));

        for (User u : new User[]{tural, leyla, sabina, rashad}) {
            Node r = nodeService.personalRootOf(u);
            file(r, "Welcome to Docket", "txt", u, SampleFiles.text("Welcome to PMIS Docket, " + u.displayName + "!", "",
                    "This is your private folder. Only you can see it.", "Company folders are under “Company”."));
        }

        // ---------- Company folders and permissions ----------
        Node company = nodeService.companyRoot();
        Node shared = folder(company, "Shared", rashad);
        acl(shared, "all", Access.WRITE);
        Node templates = folder(shared, "Templates", rashad);
        acl(templates, "all", Access.READ);
        acl(templates, "group:legal", Access.FULL);
        file(templates, "NDA template", "pdf", rashad, SampleFiles.pdf("NDA template", "Fill in the parties and the date.", "Approved by Legal."));
        Node policies = folder(shared, "Policies", rashad);
        acl(policies, "all", Access.READ);
        acl(policies, "group:hr", Access.FULL);
        file(policies, "Code of conduct", "pdf", leyla, SampleFiles.pdf("Code of conduct", "Treat colleagues and clients with respect.", "Protect company information."));
        file(policies, "Remote work policy", "pdf", leyla, SampleFiles.pdf("Remote work policy", "Up to two days a week from home.", "Use the company VPN."));
        Node common = folder(shared, "Common", rashad);
        file(common, "Phone list", "csv", sabina, SampleFiles.text("Name,Department,Phone", "Aylin Karimova,Finance,101", "Tural Mammadli,Management,102", "Leyla Rahimli,HR,103"));
        file(common, "Office move plan", "pdf", sabina, SampleFiles.pdf("Office move plan", "Move date: 14 November 2026.", "Pack personal items by 12 November."));
        Node software = folder(shared, "Software", rashad);
        acl(software, "all", Access.READ);
        file(software, "Read me first", "txt", rashad, SampleFiles.text("Software on this share is approved by IT.", "Install only what you need for work."));

        Node departments = folder(company, "Departments", rashad);
        acl(departments, "all", Access.READ);
        Node finance = folder(departments, "Finance", rashad);
        acl(finance, "group:finance", Access.WRITE);
        acl(finance, "group:management", Access.READ);
        acl(finance, "all", Access.NONE);
        Node budgets = folder(finance, "Budgets", aylin);
        Node q3 = file(budgets, "Q3 forecast", "csv", aylin, SampleFiles.text("Month,Revenue,Cost", "July,412000,355000", "August,398000,341000", "September,436000,362000"));
        Node reports = folder(finance, "Reports", aylin);
        file(reports, "Monthly report September", "pdf", aylin, SampleFiles.pdf("Monthly report - September 2026", "Revenue up 9% on August.", "Costs within budget."));
        file(finance, "Finance procedures", "pdf", tural, SampleFiles.pdf("Finance procedures", "All invoices need two approvals.", "Expense claims by the 5th of each month."));
        Node hr = folder(departments, "HR", rashad);
        acl(hr, "group:hr", Access.FULL);
        acl(hr, "all", Access.NONE);
        file(hr, "Salary bands 2027", "pdf", leyla, SampleFiles.pdf("Salary bands 2027", "Confidential."));
        Node legal = folder(departments, "Legal", rashad);
        acl(legal, "group:legal", Access.FULL);
        acl(legal, "all", Access.READ);
        file(legal, "Standard terms", "pdf", rashad, SampleFiles.pdf("Standard terms and conditions", "Payment within 30 days of invoice."));
        Node itf = folder(departments, "IT", rashad);
        acl(itf, "group:it", Access.FULL);
        acl(itf, "all", Access.NONE);

        Node archive = folder(company, "Archive", rashad);
        acl(archive, "all", Access.READ);
        Node y2025 = folder(archive, "2025", rashad);
        file(y2025, "Annual report 2025", "pdf", tural, SampleFiles.pdf("Annual report 2025", "A year of growth for PMIS."));

        Node management = folder(company, "Management", rashad);
        acl(management, "group:management", Access.FULL);
        acl(management, "all", Access.NONE);
        file(management, "Board minutes - September", "pdf", tural, SampleFiles.pdf("Board minutes - September 2026", "Confidential."));

        // Versions, check-out, sharing and an access request, so every feature has something to show.
        nodeService.addVersion(lease, storage.store(SampleFiles.pdf("Commercial lease agreement 2026",
                "Between PMIS (tenant) and the landlord.", "Term: 1 January 2026 - 31 December 2026.",
                "Monthly rent and service charges as per Schedule A.", "Updated: rent review clause added (section 7).")), "pdf", aylin, "Added rent review clause");
        q3.checkedOutBy = tural.id;
        q3.checkedOutAt = Instant.now().minus(2, ChronoUnit.HOURS);
        nodes.save(q3);
        Share sh = new Share();
        sh.nodeId = lease.id;
        sh.ownerId = aylin.id;
        sh.withUserId = tural.id;
        sh.canEdit = false;
        sh.createdAt = Instant.now();
        sh.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        shares.save(sh);
        Node turalRoot = nodeService.personalRootOf(tural);
        Node pack = file(turalRoot, "Q4 board pack", "pdf", tural, SampleFiles.pdf("Q4 board pack", "Agenda, budget and risks for the Q4 board meeting."));
        Share s2 = new Share();
        s2.nodeId = pack.id;
        s2.ownerId = tural.id;
        s2.withUserId = aylin.id;
        s2.canEdit = true;
        s2.createdAt = Instant.now();
        shares.save(s2);
        AccessRequest req = new AccessRequest();
        req.nodeId = management.id;
        req.userId = sabina.id;
        req.level = Access.READ;
        req.note = "I need the office move budget";
        req.createdAt = Instant.now().minus(1, ChronoUnit.DAYS);
        requests.save(req);

        log.info("Demo data ready. Sign in as a.karimova (user) or r.aliyev (IT admin).");
    }

    private User user(String login, String name, String dept, String group, Role role, boolean active) {
        User u = new User();
        u.login = login;
        u.displayName = name;
        u.department = dept;
        u.groupKey = group;
        u.role = role;
        u.active = active;
        u.quotaBytes = (role == Role.ADMIN ? 50L : 20L) * 1024 * 1024 * 1024;
        u.passwordHash = auth.hash(DEMO_PASSWORD);
        return users.save(u);
    }

    private Node folder(Node parent, String name, User by) {
        Node f = new Node();
        f.parentId = parent.id;
        f.type = NodeType.FOLDER;
        f.space = parent.space;
        f.ownerId = by.id;
        f.name = name;
        f.createdAt = f.modifiedAt = tick();
        f.modifiedBy = by.id;
        return nodes.save(f);
    }

    private Node file(Node parent, String name, String ext, User by, byte[] bytes) {
        Node f = nodeService.addFile(parent, name, ext, storage.store(bytes), by, "Uploaded");
        f.createdAt = f.modifiedAt = tick();
        return nodes.save(f);
    }

    private void acl(Node node, String principal, Access access) {
        acls.save(new AclEntry(node.id, principal, access));
    }

    private Instant tick() {
        clock = clock.plus(7, ChronoUnit.HOURS);
        return clock;
    }
}
