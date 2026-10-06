package com.pmis.docket.server.service;

import com.pmis.docket.server.auth.AuthService;
import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.model.*;
import com.pmis.docket.server.repo.*;
import com.pmis.docket.server.web.ApiException;
import com.pmis.docket.server.web.Dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Everything in the IT admin console. All methods require an administrator. */
@Service
public class AdminService {
    private static final String[] GROUP_KEYS = {"finance", "hr", "legal", "management", "operations", "it"};
    private final UserRepository users;
    private final NodeRepository nodes;
    private final AclRepository acls;
    private final AccessRequestRepository requests;
    private final AuditRepository auditRepo;
    private final FileVersionRepository versions;
    private final ShareRepository shares;
    private final NodeService nodeService;
    private final AuthService auth;
    private final AuditService audit;
    private final StorageService storage;
    private final ToolService tools;
    private final DocketProperties props;
    private final SessionRepository sessions;

    public AdminService(UserRepository users, NodeRepository nodes, AclRepository acls, AccessRequestRepository requests,
                        AuditRepository auditRepo, FileVersionRepository versions, ShareRepository shares, NodeService nodeService,
                        AuthService auth, AuditService audit, StorageService storage, ToolService tools, DocketProperties props,
                        SessionRepository sessions) {
        this.sessions = sessions;
        this.users = users;
        this.nodes = nodes;
        this.acls = acls;
        this.requests = requests;
        this.auditRepo = auditRepo;
        this.versions = versions;
        this.shares = shares;
        this.nodeService = nodeService;
        this.auth = auth;
        this.audit = audit;
        this.storage = storage;
        this.tools = tools;
        this.props = props;
    }

    public static void requireAdmin(User u) {
        if (!u.isAdmin()) throw ApiException.forbidden("Only IT administrators can do this.");
    }

    // ================================================================== users

    @Transactional(readOnly = true)
    public List<AdminUserDto> users(User admin) {
        requireAdmin(admin);
        List<AdminUserDto> out = new ArrayList<>();
        for (User u : users.findAll()) out.add(toDto(u));
        out.sort(Comparator.comparing(AdminUserDto::displayName));
        return out;
    }

    @Transactional
    public TempPasswordDto createUser(User admin, String computer, UserRequest r) {
        requireAdmin(admin);
        String login = AuthService.normalizeLogin(r.login());
        if (login.isBlank() || !login.matches("[a-z0-9._-]{2,60}")) throw ApiException.badRequest("Use a username with letters, numbers, dots or dashes (e.g. n.mammadova).");
        if (users.findByLoginIgnoreCase(login).isPresent()) throw ApiException.conflict("The username “" + login + "” is already taken.");
        if (r.displayName() == null || r.displayName().isBlank()) throw ApiException.badRequest("Type the person’s full name.");
        User u = new User();
        u.login = login;
        u.displayName = r.displayName().trim();
        u.department = blankToNull(r.department());
        u.groupKey = groupKey(r.groupKey(), r.department());
        u.role = "ADMIN".equalsIgnoreCase(r.role()) ? Role.ADMIN : Role.USER;
        u.quotaBytes = (r.quotaGb() == null ? 20 : Math.max(1, r.quotaGb())) * 1024L * 1024 * 1024;
        u.active = true;
        String temp = tempPassword();
        u.passwordHash = auth.hash(temp);
        u.mustChangePassword = true;
        users.save(u);
        nodeService.personalRootOf(u);
        audit.record(admin, computer, "Created user " + u.displayName + " (" + u.login + ")", "Permissions", null);
        return new TempPasswordDto(toDto(u), temp);
    }

    @Transactional
    public AdminUserDto updateUser(User admin, String computer, Long id, UserRequest r) {
        requireAdmin(admin);
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("This user"));
        List<String> changes = new ArrayList<>();
        if (r.displayName() != null && !r.displayName().isBlank() && !r.displayName().trim().equals(u.displayName)) {
            u.displayName = r.displayName().trim();
            changes.add("name");
        }
        if (r.department() != null && !Objects.equals(blankToNull(r.department()), u.department)) {
            u.department = blankToNull(r.department());
            changes.add("department → " + (u.department == null ? "none" : u.department));
        }
        if (r.groupKey() != null || r.department() != null) {
            String g = groupKey(r.groupKey(), u.department);
            if (!Objects.equals(g, u.groupKey)) {
                u.groupKey = g;
                changes.add("group → " + g);
            }
        }
        if (r.role() != null) {
            Role role = "ADMIN".equalsIgnoreCase(r.role()) ? Role.ADMIN : Role.USER;
            if (role != u.role) {
                if (u.id.equals(admin.id) && role == Role.USER) throw ApiException.badRequest("You can’t remove your own administrator role.");
                u.role = role;
                changes.add("role → " + (role == Role.ADMIN ? "IT administrator" : "user"));
            }
        }
        if (r.quotaGb() != null && r.quotaGb() > 0 && r.quotaGb() * 1024L * 1024 * 1024 != u.quotaBytes) {
            u.quotaBytes = r.quotaGb() * 1024L * 1024 * 1024;
            changes.add("storage → " + r.quotaGb() + " GB");
        }
        if (r.active() != null && r.active() != u.active) {
            if (u.id.equals(admin.id) && !r.active()) throw ApiException.badRequest("You can’t disable your own account.");
            u.active = r.active();
            if (!u.active) sessions.deleteByUserId(u.id);
            changes.add(u.active ? "enabled" : "disabled (signed out everywhere)");
        }
        users.save(u);
        if (!changes.isEmpty()) audit.record(admin, computer, "Updated user " + u.displayName + ": " + String.join(", ", changes), "Permissions", null);
        return toDto(u);
    }

    @Transactional
    public TempPasswordDto resetPassword(User admin, String computer, Long id) {
        requireAdmin(admin);
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("This user"));
        String temp = tempPassword();
        u.passwordHash = auth.hash(temp);
        u.mustChangePassword = true;
        users.save(u);
        audit.record(admin, computer, "Reset password for " + u.displayName, "Permissions", null);
        return new TempPasswordDto(toDto(u), temp);
    }

    /**
     * Deletes an account. Their My files are either moved to a colleague (into a folder "From <name>")
     * or moved to the Recycle Bin. Their shares, sessions, requests and folder permission lines are removed,
     * files they had checked out are released. The audit log keeps everything they did.
     */
    @Transactional
    public String deleteUser(User admin, String computer, Long id, Long transferTo, boolean deleteFiles) {
        requireAdmin(admin);
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("This user"));
        if (u.id.equals(admin.id)) throw ApiException.badRequest("You can’t delete your own account.");
        if (u.isAdmin() && users.findAll().stream().filter(x -> x.isAdmin() && x.active && !x.id.equals(u.id)).count() == 0) {
            throw ApiException.badRequest("This is the last IT administrator. Make someone else an administrator first.");
        }
        Node root = nodes.findFirstByTypeAndSpaceAndOwnerId(NodeType.ROOT, Space.PERSONAL, u.id).orElse(null);
        List<Node> items = root == null ? List.of() : nodes.findByParentIdAndDeletedFalse(root.id);
        String filesNote;
        if (items.isEmpty()) {
            filesNote = "My files was empty";
        } else if (transferTo != null) {
            User to = users.findById(transferTo).orElseThrow(() -> ApiException.notFound("The person receiving the files"));
            if (to.id.equals(u.id)) throw ApiException.badRequest("Choose someone else to receive the files.");
            Node toRoot = nodeService.personalRootOf(to);
            Node folder = new Node();
            folder.parentId = toRoot.id;
            folder.type = NodeType.FOLDER;
            folder.space = Space.PERSONAL;
            folder.ownerId = to.id;
            folder.name = nodeService.uniqueName(toRoot.id, "From " + u.displayName, null);
            folder.createdAt = folder.modifiedAt = Instant.now();
            folder.modifiedBy = admin.id;
            nodes.save(folder);
            for (Node n : items) {
                n.parentId = folder.id;
                nodes.save(n);
            }
            filesNote = items.size() + " items moved to " + to.displayName + "’s My files › " + folder.name;
        } else if (deleteFiles) {
            Instant now = Instant.now();
            for (Node n : items) {
                markDeleted(n, now, admin.id);
                n.deleteRoot = true;
                nodes.save(n);
            }
            filesNote = items.size() + " items moved to the Recycle Bin";
        } else {
            throw ApiException.badRequest("Choose who receives their files, or delete the files.");
        }
        for (Share sh : shares.findByOwnerId(u.id)) shares.delete(sh);
        for (Share sh : shares.findByWithUserId(u.id)) shares.delete(sh);
        for (AccessRequest r : requests.findByUserId(u.id)) requests.delete(r);
        for (AclEntry a : acls.findAll()) if (a.principal.equals("user:" + u.id)) acls.delete(a);
        for (Node n : nodes.findAll()) {
            if (u.id.equals(n.checkedOutBy)) {
                n.checkedOutBy = null;
                n.checkedOutAt = null;
                nodes.save(n);
            }
        }
        sessions.deleteByUserId(u.id);
        String name = u.displayName + " (" + u.login + ")";
        users.delete(u);
        String msg = "Deleted " + name + ". " + filesNote + ".";
        audit.record(admin, computer, msg, "Permissions", null);
        return msg;
    }

    private void markDeleted(Node n, Instant when, Long by) {
        n.deleted = true;
        n.deletedAt = when;
        n.deletedBy = by;
        nodes.save(n);
        for (Node c : nodes.findByParentIdAndDeletedFalse(n.id)) markDeleted(c, when, by);
    }

    // ================================================================== folder permissions

    @Transactional(readOnly = true)
    public List<FolderDto> folders(User admin) {
        requireAdmin(admin);
        List<FolderDto> out = new ArrayList<>();
        walk(nodeService.companyRoot().id, 0, out);
        return out;
    }

    private void walk(Long parentId, int depth, List<FolderDto> out) {
        if (depth > 4) return;
        List<Node> kids = new ArrayList<>(nodes.findByParentIdAndDeletedFalse(parentId));
        kids.sort(Comparator.comparing(n -> n.name.toLowerCase(Locale.ROOT)));
        for (Node n : kids) {
            if (n.isFile()) continue;
            out.add(new FolderDto(n.id, n.name, depth, !acls.findByNodeId(n.id).isEmpty()));
            walk(n.id, depth + 1, out);
        }
    }

    @Transactional(readOnly = true)
    public AclDto acl(User admin, Long nodeId) {
        requireAdmin(admin);
        Node n = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("This folder"));
        if (n.space != Space.COMPANY || n.type == NodeType.ROOT) throw ApiException.badRequest("Choose a company folder.");
        List<AclEntry> own = acls.findByNodeId(n.id);
        Node source = n;
        while (source != null && source.type != NodeType.ROOT && acls.findByNodeId(source.id).isEmpty()) {
            source = source.parentId == null ? null : nodes.findById(source.parentId).orElse(null);
        }
        boolean inherited = own.isEmpty();
        List<AclEntry> effective = source == null || source.type == NodeType.ROOT ? List.of() : acls.findByNodeId(source.id);
        List<AclLineDto> lines = new ArrayList<>();
        lines.add(new AclLineDto("group:it", "IT administrators", "FULL", true));
        Set<String> present = new HashSet<>();
        for (AclEntry e : effective) {
            if (e.principal.equals("group:it")) continue;
            lines.add(new AclLineDto(e.principal, nodeService.principalName(e.principal), e.access.name(), false));
            present.add(e.principal);
        }
        if (effective.isEmpty()) lines.add(new AclLineDto("all", "All staff", "READ", false));
        List<AclLineDto> addable = new ArrayList<>();
        if (!present.contains("all") && !effective.isEmpty()) addable.add(new AclLineDto("all", "All staff", "READ", false));
        for (String g : GROUP_KEYS) {
            if (g.equals("it") || present.contains("group:" + g)) continue;
            addable.add(new AclLineDto("group:" + g, nodeService.principalName("group:" + g), "READ", false));
        }
        for (User u : users.findAll()) {
            if (!u.active || u.isAdmin() || present.contains("user:" + u.id)) continue;
            addable.add(new AclLineDto("user:" + u.id, u.displayName + " (person)", "READ", false));
        }
        return new AclDto(n.id, n.name, nodeService.pathText(n.id), inherited,
                inherited && source != null && source.type != NodeType.ROOT ? source.name : null, lines, addable);
    }

    @Transactional
    public AclDto setAcl(User admin, String computer, Long nodeId, String principal, String access) {
        requireAdmin(admin);
        Node n = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("This folder"));
        if (n.space != Space.COMPANY || n.type == NodeType.ROOT) throw ApiException.badRequest("Choose a company folder.");
        if (principal == null || principal.equals("group:it")) throw ApiException.badRequest("IT administrators always have full control.");
        Access level;
        try {
            level = Access.valueOf(access);
        } catch (Exception e) {
            throw ApiException.badRequest("Unknown access level.");
        }
        copyInheritedIfNeeded(n);
        AclEntry line = acls.findByNodeId(n.id).stream().filter(a -> a.principal.equals(principal)).findFirst().orElse(null);
        if (line == null) line = new AclEntry(n.id, principal, level);
        line.access = level;
        acls.save(line);
        audit.record(admin, computer, "Changed permissions: " + nodeService.principalName(principal) + " → " + level.label(), "Permissions", n, nodeService.pathText(n.id));
        return acl(admin, nodeId);
    }

    @Transactional
    public AclDto removeAcl(User admin, String computer, Long nodeId, String principal) {
        requireAdmin(admin);
        Node n = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("This folder"));
        copyInheritedIfNeeded(n);
        for (AclEntry e : acls.findByNodeId(n.id)) {
            if (e.principal.equals(principal)) acls.delete(e);
        }
        audit.record(admin, computer, "Removed " + nodeService.principalName(principal) + " from permissions", "Permissions", n, nodeService.pathText(n.id));
        return acl(admin, nodeId);
    }

    @Transactional
    public AclDto inherit(User admin, String computer, Long nodeId) {
        requireAdmin(admin);
        Node n = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("This folder"));
        for (AclEntry e : acls.findByNodeId(n.id)) acls.delete(e);
        audit.record(admin, computer, "Permissions now inherited from the parent folder", "Permissions", n, nodeService.pathText(n.id));
        return acl(admin, nodeId);
    }

    /** Before the first own change, copy the inherited lines so the folder keeps behaving the same. */
    private void copyInheritedIfNeeded(Node n) {
        if (!acls.findByNodeId(n.id).isEmpty()) return;
        Node source = n.parentId == null ? null : nodes.findById(n.parentId).orElse(null);
        while (source != null && source.type != NodeType.ROOT && acls.findByNodeId(source.id).isEmpty()) {
            source = source.parentId == null ? null : nodes.findById(source.parentId).orElse(null);
        }
        if (source == null || source.type == NodeType.ROOT) {
            acls.save(new AclEntry(n.id, "all", Access.READ));
            return;
        }
        for (AclEntry e : acls.findByNodeId(source.id)) acls.save(new AclEntry(n.id, e.principal, e.access));
    }

    // ================================================================== access requests

    @Transactional(readOnly = true)
    public List<RequestDto> requests(User admin) {
        requireAdmin(admin);
        List<RequestDto> out = new ArrayList<>();
        for (AccessRequest r : requests.findAllByOrderByCreatedAtDesc()) {
            String who = users.findById(r.userId).map(u -> u.displayName).orElse("—");
            String folder = nodes.findById(r.nodeId).map(n -> nodeService.pathText(n.id)).orElse("(deleted folder)");
            out.add(new RequestDto(r.id, who, r.nodeId, folder, r.level.label(), r.note, r.createdAt, r.status.name()));
        }
        return out;
    }

    @Transactional
    public RequestDto decide(User admin, String computer, Long id, boolean approve) {
        requireAdmin(admin);
        AccessRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("This request"));
        if (r.status != AccessRequest.Status.PENDING) throw ApiException.conflict("This request was already handled.");
        r.status = approve ? AccessRequest.Status.APPROVED : AccessRequest.Status.DENIED;
        r.decidedBy = admin.id;
        r.decidedAt = Instant.now();
        requests.save(r);
        User who = users.findById(r.userId).orElse(null);
        Node n = nodes.findById(r.nodeId).orElse(null);
        if (approve && n != null && who != null) setAcl(admin, computer, n.id, "user:" + who.id, r.level.name());
        audit.record(admin, computer, (approve ? "Approved " : "Denied ") + r.level.label().toLowerCase(Locale.ROOT) + " access for "
                + (who == null ? "—" : who.displayName), "Permissions", n, n == null ? null : nodeService.pathText(n.id));
        return requests(admin).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    // ================================================================== audit

    @Transactional(readOnly = true)
    public List<AuditDto> audit(User admin, String kind, String q, int limit) {
        requireAdmin(admin);
        String k = kind == null || kind.equals("All") ? "" : kind;
        String query = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Map<String, String> names = new HashMap<>();
        List<AuditDto> out = new ArrayList<>();
        for (AuditEvent e : auditRepo.search(k, query, PageRequest.of(0, Math.max(1, Math.min(limit, 2000))))) {
            String who = e.userLogin == null ? "—" : names.computeIfAbsent(e.userLogin, l -> users.findByLoginIgnoreCase(l).map(u -> u.displayName).orElse(l));
            out.add(new AuditDto(e.id, e.occurredAt, who, e.action, e.kind, e.nodePath, e.computer));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public String auditCsv(User admin, String computer, String kind, String q) {
        StringBuilder b = new StringBuilder("\uFEFFTime,User,Kind,Action,Item,Computer\r\n");
        for (AuditDto a : audit(admin, kind, q, 100000)) {
            b.append(csv(a.at() == null ? "" : a.at().toString())).append(',').append(csv(a.user())).append(',').append(csv(a.kind())).append(',')
                    .append(csv(a.action())).append(',').append(csv(a.item())).append(',').append(csv(a.computer())).append("\r\n");
        }
        audit.record(admin, computer, "Exported the audit log", "Permissions", null);
        return b.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ================================================================== server and recycle bin

    @Transactional(readOnly = true)
    public ServerStatsDto stats(User admin) {
        requireAdmin(admin);
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long activeToday = users.findAll().stream().filter(u -> u.lastSignIn != null && u.lastSignIn.isAfter(startOfDay)).count();
        return new ServerStatsDto(storage.totalBytes(), storage.freeBytes(), nodes.sumAll(NodeType.FILE), nodes.sumDeleted(NodeType.FILE),
                users.count(), activeToday, nodes.countByCheckedOutByIsNotNullAndDeletedFalse(),
                requests.countByStatus(AccessRequest.Status.PENDING), tools.hasOffice(), tools.hasFfmpeg(), storage.root().toString());
    }

    @Transactional(readOnly = true)
    public List<RecycleDto> recycleBin(User admin) {
        requireAdmin(admin);
        List<RecycleDto> out = new ArrayList<>();
        for (Node n : nodes.findByDeletedTrueAndDeleteRootTrueOrderByDeletedAtDesc()) {
            String by = n.deletedBy == null ? "—" : users.findById(n.deletedBy).map(u -> u.displayName).orElse("—");
            String where = n.parentId == null ? "" : nodeService.pathText(n.parentId);
            out.add(new RecycleDto(n.id, n.fullName(), n.isFile() ? "File" : "Folder", where, by, n.deletedAt, n.sizeBytes));
        }
        return out;
    }

    /** Removes items deleted more than {@code recycleDays} ago, including their contents on disk when nothing else uses them. */
    @Transactional
    public String purge(User admin, String computer) {
        requireAdmin(admin);
        Instant cutoff = Instant.now().minus(props.getRecycleDays(), ChronoUnit.DAYS);
        List<Node> old = nodes.findByDeletedTrueAndDeletedAtBefore(cutoff);
        Set<String> blobs = new HashSet<>();
        long freed = 0;
        for (Node n : old) {
            for (FileVersion v : versions.findByNodeId(n.id)) {
                blobs.add(v.blobKey);
                versions.delete(v);
            }
            for (Share s : shares.findByNodeId(n.id)) shares.delete(s);
            for (AclEntry a : acls.findByNodeId(n.id)) acls.delete(a);
            if (n.blobKey != null) blobs.add(n.blobKey);
            nodes.delete(n);
        }
        nodes.flush();
        for (String key : blobs) {
            if (nodes.countByBlobKey(key) == 0 && versions.countByBlobKey(key) == 0) {
                try {
                    long size = Files.size(storage.pathFor(key));
                    Files.deleteIfExists(storage.pathFor(key));
                    freed += size;
                } catch (IOException ignored) {
                    // already gone
                }
            }
        }
        String msg = "Removed " + old.size() + " items deleted more than " + props.getRecycleDays() + " days ago. Freed " + NodeService.human(freed) + ".";
        audit.record(admin, computer, "Emptied Recycle Bin: " + msg, "Deleted", null);
        return msg;
    }

    // ================================================================== helpers

    private AdminUserDto toDto(User u) {
        long used = nodes.sumSize(Space.PERSONAL, u.id, NodeType.FILE);
        return new AdminUserDto(u.id, u.login, u.displayName, u.initials(), u.department, u.groupKey, u.role.name(), u.active,
                used, u.quotaBytes, u.lastSignIn, u.lastComputer);
    }

    private static String groupKey(String explicit, String department) {
        if (explicit != null && !explicit.isBlank()) return explicit.trim().toLowerCase(Locale.ROOT);
        if (department == null) return null;
        String d = department.trim().toLowerCase(Locale.ROOT);
        for (String g : GROUP_KEYS) if (d.equals(g) || d.startsWith(g)) return g;
        return d.replaceAll("[^a-z0-9]", "");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String tempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(chars.charAt(r.nextInt(chars.length())));
        return b.substring(0, 5) + "-" + b.substring(5);
    }
}
