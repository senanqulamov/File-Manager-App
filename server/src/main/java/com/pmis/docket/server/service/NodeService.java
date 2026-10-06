package com.pmis.docket.server.service;

import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.model.*;
import com.pmis.docket.server.repo.*;
import com.pmis.docket.server.web.ApiException;
import com.pmis.docket.server.web.Dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Browsing and changing folders and files, versions, check-out, sharing and access requests. */
@Service
public class NodeService {
    private static final Pattern BAD_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    static final Map<String, String> GROUP_NAMES = Map.of(
            "finance", "Finance team", "hr", "HR team", "legal", "Legal team",
            "management", "Management", "it", "IT administrators", "operations", "Operations team");

    private final NodeRepository nodes;
    private final UserRepository users;
    private final AclRepository acls;
    private final AuditRepository auditRepo;
    private final FileVersionRepository versions;
    private final ShareRepository shares;
    private final AccessRequestRepository requests;
    private final PermissionService perms;
    private final StorageService storage;
    private final AuditService audit;
    private final DocketProperties props;

    public NodeService(NodeRepository nodes, UserRepository users, AclRepository acls, AuditRepository auditRepo,
                       FileVersionRepository versions, ShareRepository shares, AccessRequestRepository requests,
                       PermissionService perms, StorageService storage, AuditService audit, DocketProperties props) {
        this.nodes = nodes;
        this.users = users;
        this.acls = acls;
        this.auditRepo = auditRepo;
        this.versions = versions;
        this.shares = shares;
        this.requests = requests;
        this.perms = perms;
        this.storage = storage;
        this.audit = audit;
        this.props = props;
    }

    // ================================================================== reading

    @Transactional
    public RootsDto roots(User user) {
        Node personal = personalRootOf(user);
        Node company = companyRoot();
        return new RootsDto(toDto(user, personal), toDto(user, company), sharedWithMe(user).size(), user.isAdmin());
    }

    @Transactional(readOnly = true)
    public NodeDto get(User user, Long id) {
        Node n = require(id);
        requireRead(user, n);
        return toDto(user, n);
    }

    @Transactional(readOnly = true)
    public List<NodeDto> children(User user, Long id) {
        Node folder = require(id);
        if (folder.isFile()) throw ApiException.badRequest("That is a file, not a folder.");
        requireRead(user, folder);
        List<NodeDto> out = new ArrayList<>();
        for (Node c : nodes.findByParentIdAndDeletedFalse(id)) out.add(toDto(user, c));
        out.sort(Comparator.comparing((NodeDto d) -> d.type().equals("FILE"))
                .thenComparing(d -> d.fullName().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Items other people shared with this user (newest first), only the top shared item of each tree. */
    @Transactional(readOnly = true)
    public List<NodeDto> sharedWithMe(User user) {
        Instant now = Instant.now();
        Map<Long, NodeDto> out = new LinkedHashMap<>();
        List<Share> list = new ArrayList<>(shares.findByWithUserId(user.id));
        list.sort(Comparator.comparing((Share s) -> s.createdAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder())));
        for (Share s : list) {
            if (!s.active(now)) continue;
            Node n = nodes.findById(s.nodeId).orElse(null);
            if (n == null || n.deleted) continue;
            Node top = perms.sharedTop(user, n);
            if (top != null && !top.id.equals(n.id)) continue;
            out.put(n.id, toDto(user, n));
        }
        return new ArrayList<>(out.values());
    }

    /** Files this user opened or changed recently. */
    @Transactional(readOnly = true)
    public List<NodeDto> recent(User user) {
        LinkedHashMap<Long, NodeDto> out = new LinkedHashMap<>();
        for (AuditEvent e : auditRepo.findByUserIdAndNodeIdIsNotNullOrderByOccurredAtDesc(user.id, PageRequest.of(0, 200))) {
            if (out.size() >= 12) break;
            if (out.containsKey(e.nodeId)) continue;
            Node n = nodes.findById(e.nodeId).orElse(null);
            if (n == null || n.deleted || !n.isFile()) continue;
            if (perms.effective(user, n) == Access.NONE) continue;
            out.put(n.id, toDto(user, n));
        }
        return new ArrayList<>(out.values());
    }

    /** Path from the top. Items shared with the user start at the shared item (the app shows "Shared with me" before it). */
    @Transactional(readOnly = true)
    public List<PathItemDto> path(User user, Long id) {
        Node n = require(id);
        requireRead(user, n);
        Node stopAt = null;
        if (n.space == Space.PERSONAL && !perms.ownsPersonal(user, n)) stopAt = perms.sharedTop(user, n);
        LinkedList<PathItemDto> out = new LinkedList<>();
        while (n != null) {
            out.addFirst(new PathItemDto(n.id, n.fullName()));
            if (stopAt != null && n.id.equals(stopAt.id)) break;
            n = parentOf(n);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PropertiesDto properties(User user, Long id) {
        Node n = require(id);
        requireRead(user, n);
        String location = n.parentId == null ? "\\\\SRV-FILES" : "\\\\SRV-FILES › " + pathText(n.parentId);
        String contains = null;
        if (!n.isFile()) {
            int[] counts = new int[2];
            countInside(n.id, counts, 0);
            contains = counts[0] + " files, " + counts[1] + " folders";
        }
        String owner;
        List<AccessRowDto> rows = new ArrayList<>();
        if (n.space == Space.PERSONAL) {
            Node root = perms.personalRoot(n);
            owner = users.findById(root.ownerId).map(u -> u.displayName).orElse("—");
            rows.add(new AccessRowDto(owner + " (owner)", "Full control"));
            Instant now = Instant.now();
            Node walk = n;
            while (walk != null && walk.type != NodeType.ROOT) {
                for (Share s : shares.findByNodeId(walk.id)) {
                    if (!s.active(now)) continue;
                    String who = users.findById(s.withUserId).map(u -> u.displayName).orElse("—");
                    String via = walk.id.equals(n.id) ? "" : " (via “" + walk.fullName() + "”)";
                    rows.add(new AccessRowDto(who + via, s.canEdit ? "Can edit" : "Can view"));
                }
                walk = parentOf(walk);
            }
            rows.add(new AccessRowDto("Everyone else", "No access"));
        } else {
            owner = n.ownerId == null ? props.getCompanyName() : users.findById(n.ownerId).map(u -> u.displayName).orElse(props.getCompanyName());
            rows.add(new AccessRowDto("IT administrators", "Full control"));
            Node withAcl = n;
            while (withAcl != null && withAcl.type != NodeType.ROOT && acls.findByNodeId(withAcl.id).isEmpty()) withAcl = parentOf(withAcl);
            if (withAcl != null && withAcl.type != NodeType.ROOT) {
                for (AclEntry e : acls.findByNodeId(withAcl.id)) {
                    if (e.principal.equals("group:it")) continue;
                    rows.add(new AccessRowDto(principalName(e.principal), e.access.label()));
                }
            } else {
                rows.add(new AccessRowDto("All staff", "Read only"));
            }
        }
        return new PropertiesDto(toDto(user, n), location, contains, owner, rows);
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> activity(User user, Long id) {
        Node n = require(id);
        requireRead(user, n);
        Map<String, String> names = new HashMap<>();
        List<ActivityDto> out = new ArrayList<>();
        for (AuditEvent e : auditRepo.findByNodeIdOrderByOccurredAtDesc(id, PageRequest.of(0, 100))) {
            String who = e.userLogin == null ? "—" : names.computeIfAbsent(e.userLogin,
                    l -> users.findByLoginIgnoreCase(l).map(u -> u.displayName).orElse(l));
            out.add(new ActivityDto(e.occurredAt, who, e.action, e.computer));
        }
        return out;
    }

    /** The file on disk (current or an older version) after checking access; records the open/download. */
    @Transactional(readOnly = true)
    public FileContent content(User user, String computer, Long id, boolean download, Integer versionNumber) {
        Node n = require(id);
        if (!n.isFile()) throw ApiException.badRequest("Folders can’t be downloaded. Open the folder instead.");
        requireRead(user, n);
        String key = n.blobKey;
        long size = n.sizeBytes;
        String name = n.fullName();
        if (versionNumber != null && versionNumber != n.version) {
            FileVersion v = versions.findByNodeIdAndNumber(n.id, versionNumber)
                    .orElseThrow(() -> ApiException.notFound("Version " + versionNumber));
            key = v.blobKey;
            size = v.sizeBytes;
            name = n.name + " (version " + v.number + ")" + (v.ext == null ? "" : "." + v.ext);
        }
        String action = (download ? "Downloaded" : "Opened") + (versionNumber != null ? " version " + versionNumber : "");
        audit.record(user, computer, action, "Opened", n, pathText(n.id));
        return new FileContent(name, storage.pathFor(key), size);
    }

    public Node requireNode(Long id) { return require(id); }

    // ================================================================== creating and changing

    @Transactional
    public NodeDto createFolder(User user, String computer, Long parentId, String rawName) {
        Node parent = require(parentId);
        requireWriteInside(user, parent);
        Node f = newNode(parent, NodeType.FOLDER, uniqueName(parent.id, cleanName(rawName), null), null, user);
        nodes.save(f);
        audit.record(user, computer, "Created folder", "Edited", f, pathText(f.id));
        return toDto(user, f);
    }

    @Transactional
    public NodeDto upload(User user, String computer, Long parentId, String originalName, InputStream in, long size) {
        Node parent = require(parentId);
        requireWriteInside(user, parent);
        checkQuota(user, parent, size);
        String[] split = splitName(originalName);
        StorageService.Stored stored = storage.store(in);
        Node f = addFile(parent, split[0], split[1], stored, user, "Uploaded");
        audit.record(user, computer, "Uploaded", "Edited", f, pathText(f.id));
        return toDto(user, f);
    }

    /** Creates a file node with version 1 from already stored contents. Name is made unique. */
    public Node addFile(Node parent, String base, String ext, StorageService.Stored stored, User by, String note) {
        Node f = newNode(parent, NodeType.FILE, uniqueName(parent.id, cleanName(base), ext), ext, by);
        f.sizeBytes = stored.size();
        f.blobKey = stored.key();
        f.version = 1;
        nodes.save(f);
        saveVersion(f, by, note);
        return f;
    }

    /** Replaces a file's contents with a new version (used by check-in, sign, lock, stamp, restore). */
    public Node addVersion(Node f, StorageService.Stored stored, String ext, User by, String note) {
        f.blobKey = stored.key();
        f.sizeBytes = stored.size();
        if (ext != null) f.ext = ext;
        f.version = f.version + 1;
        f.modifiedAt = Instant.now();
        f.modifiedBy = by.id;
        nodes.save(f);
        saveVersion(f, by, note);
        return f;
    }

    @Transactional
    public NodeDto rename(User user, String computer, Long id, String rawName) {
        Node n = require(id);
        if (n.type == NodeType.ROOT) throw ApiException.forbidden("This location can’t be renamed.");
        Node parent = require(n.parentId);
        requireWriteInside(user, parent);
        requireNotCheckedOutByOther(user, n);
        String name = rawName == null ? "" : rawName.trim();
        if (n.isFile() && n.ext != null && name.toLowerCase(Locale.ROOT).endsWith("." + n.ext)) {
            name = name.substring(0, name.length() - n.ext.length() - 1);
        }
        name = cleanName(name);
        if (name.equals(n.name)) return toDto(user, n);
        String full = n.ext == null ? name : name + "." + n.ext;
        for (Node s : nodes.findByParentIdAndDeletedFalse(parent.id)) {
            if (!s.id.equals(n.id) && s.fullName().equalsIgnoreCase(full)) {
                throw ApiException.conflict("Something called “" + full + "” already exists here.");
            }
        }
        String old = n.fullName();
        n.name = name;
        n.modifiedAt = Instant.now();
        n.modifiedBy = user.id;
        nodes.save(n);
        audit.record(user, computer, "Renamed from “" + old + "”", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    @Transactional
    public void delete(User user, String computer, Long id) {
        Node n = require(id);
        if (n.type == NodeType.ROOT) throw ApiException.forbidden("This location can’t be deleted.");
        Node parent = require(n.parentId);
        requireWriteInside(user, parent);
        requireNotCheckedOutByOther(user, n);
        String where = pathText(n.id);
        Instant now = Instant.now();
        markDeleted(n, now, user.id);
        n.deleteRoot = true;
        nodes.save(n);
        audit.record(user, computer, "Deleted “" + n.fullName() + "” (moved to Recycle Bin)", "Deleted", n, where);
    }

    /** Puts a deleted item back. Allowed for the person who deleted it, or IT. */
    @Transactional
    public NodeDto restore(User user, String computer, Long id) {
        Node n = nodes.findById(id).orElseThrow(() -> ApiException.notFound("This item"));
        if (!n.deleted) return toDto(user, n);
        if (!user.isAdmin() && !user.id.equals(n.deletedBy)) throw ApiException.forbidden("Only the person who deleted it or IT can restore it.");
        Node parent = nodes.findById(n.parentId).orElse(null);
        if (parent == null || parent.deleted) parent = n.space == Space.PERSONAL ? perms.personalRoot(n) : companyRoot();
        if (n.space == Space.PERSONAL) {
            Node root = perms.personalRoot(parent);
            if (root == null || root.ownerId == null || users.findById(root.ownerId).isEmpty()) parent = personalRootOf(user);
        }
        n.parentId = parent.id;
        n.name = uniqueName(parent.id, n.name, n.ext);
        restoreTree(n, n.deletedAt);
        n.deleteRoot = false;
        nodes.save(n);
        audit.record(user, computer, "Restored from Recycle Bin", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    @Transactional
    public NodeDto copy(User user, String computer, Long id, Long targetId) {
        Node src = require(id);
        requireRead(user, src);
        if (src.type == NodeType.ROOT) throw ApiException.badRequest("This location can’t be copied.");
        Node target = require(targetId);
        requireWriteInside(user, target);
        if (!src.isFile() && isInside(target, src)) throw ApiException.badRequest("A folder can’t be copied into itself.");
        checkQuota(user, target, treeSize(src));
        Node copy = copyTree(src, target, user, true);
        audit.record(user, computer, "Copied from “" + pathText(src.id) + "”", "Edited", copy, pathText(copy.id));
        return toDto(user, copy);
    }

    @Transactional
    public NodeDto move(User user, String computer, Long id, Long targetId) {
        Node n = require(id);
        if (n.type == NodeType.ROOT) throw ApiException.badRequest("This location can’t be moved.");
        Node from = require(n.parentId);
        Node target = require(targetId);
        requireWriteInside(user, from);
        requireWriteInside(user, target);
        requireNotCheckedOutByOther(user, n);
        if (target.id.equals(from.id)) return toDto(user, n);
        if (!n.isFile() && isInside(target, n)) throw ApiException.badRequest("A folder can’t be moved into itself.");
        if (target.space != n.space) checkQuota(user, target, treeSize(n));
        String old = pathText(n.id);
        n.parentId = target.id;
        n.name = uniqueName(target.id, n.name, n.ext);
        setSpace(n, target.space);
        nodes.save(n);
        audit.record(user, computer, "Moved from “" + old + "”", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    // ================================================================== check-out and versions

    @Transactional
    public NodeDto checkout(User user, String computer, Long id) {
        Node n = requireFile(id);
        requireModify(user, n);
        if (n.checkedOutBy != null && n.checkedOutBy.equals(user.id)) return toDto(user, n);
        n.checkedOutBy = user.id;
        n.checkedOutAt = Instant.now();
        nodes.save(n);
        audit.record(user, computer, "Checked out for editing", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    /** Ends a check-out. With new contents a new version is saved; without, the check-out is just released. */
    @Transactional
    public NodeDto checkin(User user, String computer, Long id, String note, String originalName, InputStream in, long size) {
        Node n = requireFile(id);
        if (n.checkedOutBy == null || !n.checkedOutBy.equals(user.id)) {
            throw ApiException.conflict("You don’t have “" + n.fullName() + "” checked out.");
        }
        String text = note == null || note.isBlank() ? "No comment" : note.trim();
        if (in != null) {
            checkQuota(user, require(n.parentId), size);
            StorageService.Stored stored = storage.store(in);
            String ext = originalName == null ? null : splitName(originalName)[1];
            addVersion(n, stored, ext == null ? n.ext : ext, user, text);
        }
        n.checkedOutBy = null;
        n.checkedOutAt = null;
        nodes.save(n);
        audit.record(user, computer, in != null ? "Checked in as version " + n.version + " — " + text : "Checked in without changes", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    @Transactional
    public NodeDto discardCheckout(User user, String computer, Long id) {
        Node n = requireFile(id);
        if (n.checkedOutBy == null) return toDto(user, n);
        if (!n.checkedOutBy.equals(user.id) && !user.isAdmin()) throw ApiException.forbidden("Only the person editing the file or IT can release it.");
        n.checkedOutBy = null;
        n.checkedOutAt = null;
        nodes.save(n);
        audit.record(user, computer, "Released check-out", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    @Transactional(readOnly = true)
    public List<VersionDto> versions(User user, Long id) {
        Node n = requireFile(id);
        requireRead(user, n);
        List<VersionDto> out = new ArrayList<>();
        for (FileVersion v : versions.findByNodeIdOrderByNumberDesc(n.id)) {
            String by = v.createdBy == null ? "—" : users.findById(v.createdBy).map(u -> u.displayName).orElse("—");
            out.add(new VersionDto(v.number, by, v.createdAt, v.note, v.sizeBytes, v.number == n.version));
        }
        return out;
    }

    @Transactional
    public NodeDto restoreVersion(User user, String computer, Long id, int number) {
        Node n = requireFile(id);
        requireModify(user, n);
        FileVersion v = versions.findByNodeIdAndNumber(n.id, number).orElseThrow(() -> ApiException.notFound("Version " + number));
        if (number == n.version) return toDto(user, n);
        addVersion(n, new StorageService.Stored(v.blobKey, v.sizeBytes), v.ext, user, "Restored from version " + number);
        audit.record(user, computer, "Restored version " + number + " (now version " + n.version + ")", "Edited", n, pathText(n.id));
        return toDto(user, n);
    }

    // ================================================================== sharing

    @Transactional(readOnly = true)
    public List<ShareDto> shares(User user, Long id) {
        Node n = require(id);
        if (!perms.ownsPersonal(user, n)) throw ApiException.forbidden("Only the owner can see who it’s shared with.");
        List<ShareDto> out = new ArrayList<>();
        Instant now = Instant.now();
        for (Share s : shares.findByNodeId(n.id)) {
            if (!s.active(now)) continue;
            User u = users.findById(s.withUserId).orElse(null);
            if (u == null) continue;
            out.add(new ShareDto(s.id, u.id, u.displayName, u.initials(), s.canEdit ? "Can edit" : "Can view", s.expiresAt));
        }
        return out;
    }

    @Transactional
    public List<ShareDto> share(User user, String computer, Long id, List<Long> userIds, boolean canEdit, Integer days) {
        Node n = require(id);
        if (n.type == NodeType.ROOT) throw ApiException.badRequest("Share a folder or file inside My files.");
        if (!perms.ownsPersonal(user, n)) throw ApiException.forbidden("Only items in your own My files can be shared. Company folders are shared by IT.");
        if (userIds == null || userIds.isEmpty()) throw ApiException.badRequest("Choose at least one colleague.");
        List<String> names = new ArrayList<>();
        for (Long uid : userIds) {
            User other = users.findById(uid).orElse(null);
            if (other == null || !other.active || other.id.equals(user.id)) continue;
            for (Share old : shares.findByNodeIdAndWithUserId(n.id, uid)) shares.delete(old);
            Share s = new Share();
            s.nodeId = n.id;
            s.ownerId = user.id;
            s.withUserId = uid;
            s.canEdit = canEdit;
            s.createdAt = Instant.now();
            s.expiresAt = days == null || days <= 0 ? null : Instant.now().plus(days, ChronoUnit.DAYS);
            shares.save(s);
            names.add(other.displayName);
        }
        if (!names.isEmpty()) {
            audit.record(user, computer, "Shared with " + String.join(", ", names) + (canEdit ? " (can edit)" : " (can view)"), "Shared", n, pathText(n.id));
        }
        return shares(user, id);
    }

    @Transactional
    public void unshare(User user, String computer, Long id, Long shareId) {
        Node n = require(id);
        if (!perms.ownsPersonal(user, n)) throw ApiException.forbidden("Only the owner can change sharing.");
        Share s = shares.findById(shareId).orElseThrow(() -> ApiException.notFound("This share"));
        if (!s.nodeId.equals(n.id)) throw ApiException.badRequest("That share belongs to another item.");
        String who = users.findById(s.withUserId).map(u -> u.displayName).orElse("someone");
        shares.delete(s);
        audit.record(user, computer, "Stopped sharing with " + who, "Shared", n, pathText(n.id));
    }

    @Transactional(readOnly = true)
    public List<PersonDto> people(User user) {
        List<PersonDto> out = new ArrayList<>();
        for (User u : users.findAll()) {
            if (!u.active || u.id.equals(user.id)) continue;
            out.add(new PersonDto(u.id, u.displayName, u.initials(), u.department));
        }
        out.sort(Comparator.comparing(PersonDto::name));
        return out;
    }

    // ================================================================== access requests

    @Transactional
    public void requestAccess(User user, String computer, Long id, String level, String note) {
        Node n = require(id);
        if (n.space != Space.COMPANY) throw ApiException.badRequest("Ask the owner to share it with you.");
        if (!requests.findByNodeIdAndUserIdAndStatus(n.id, user.id, AccessRequest.Status.PENDING).isEmpty()) {
            throw ApiException.conflict("You already asked for access to “" + n.fullName() + "”. IT will reply soon.");
        }
        AccessRequest r = new AccessRequest();
        r.nodeId = n.id;
        r.userId = user.id;
        r.level = "WRITE".equalsIgnoreCase(level) ? Access.WRITE : Access.READ;
        r.note = note == null ? "" : note.trim();
        r.createdAt = Instant.now();
        requests.save(r);
        audit.record(user, computer, "Requested " + r.level.label().toLowerCase(Locale.ROOT) + " access", "Permissions", n, pathText(n.id));
    }

    // ================================================================== helpers (also used by other services)

    public Node personalRootOf(User user) {
        return nodes.findFirstByTypeAndSpaceAndOwnerId(NodeType.ROOT, Space.PERSONAL, user.id).orElseGet(() -> {
            Node r = new Node();
            r.type = NodeType.ROOT;
            r.space = Space.PERSONAL;
            r.ownerId = user.id;
            r.name = "My files";
            r.createdAt = r.modifiedAt = Instant.now();
            return nodes.save(r);
        });
    }

    public Node companyRoot() {
        return nodes.findFirstByTypeAndSpace(NodeType.ROOT, Space.COMPANY).orElseGet(() -> {
            Node r = new Node();
            r.type = NodeType.ROOT;
            r.space = Space.COMPANY;
            r.name = "Company";
            r.createdAt = r.modifiedAt = Instant.now();
            return nodes.save(r);
        });
    }

    public Node requireFile(Long id) {
        Node n = require(id);
        if (!n.isFile()) throw ApiException.badRequest("Choose a file.");
        return n;
    }

    public void requireRead(User user, Node n) {
        if (perms.effective(user, n) == Access.NONE) {
            throw ApiException.forbidden("You don’t have permission to open “" + n.fullName() + "”.");
        }
    }

    /** Changing a file's contents: needs write access and nobody else editing it. */
    public void requireModify(User user, Node n) {
        if (!perms.effective(user, n).atLeast(Access.WRITE)) {
            throw ApiException.forbidden("You have read-only access to “" + n.fullName() + "”.");
        }
        requireNotCheckedOutByOther(user, n);
    }

    public void requireWriteInside(User user, Node folder) {
        if (folder.isFile()) throw ApiException.badRequest("Choose a folder.");
        if (!perms.canWriteInside(user, folder)) {
            Access a = perms.effective(user, folder);
            throw ApiException.forbidden("You have " + a.label().toLowerCase(Locale.ROOT) + " access to “" + folder.fullName() + "”, so you can’t change it.");
        }
    }

    public void requireNotCheckedOutByOther(User user, Node n) {
        if (n.checkedOutBy != null && !n.checkedOutBy.equals(user.id)) {
            String who = users.findById(n.checkedOutBy).map(u -> u.displayName).orElse("Someone");
            throw ApiException.conflict(who + " is editing “" + n.fullName() + "” right now.");
        }
    }

    public boolean canWriteInside(User user, Node folder) { return perms.canWriteInside(user, folder); }

    public void checkQuota(User user, Node target, long addBytes) {
        if (target.space != Space.PERSONAL) return;
        Node root = perms.personalRoot(target);
        User owner = root == null ? user : users.findById(root.ownerId).orElse(user);
        long used = nodes.sumSize(Space.PERSONAL, owner.id, NodeType.FILE);
        if (used + addBytes > owner.quotaBytes) {
            throw ApiException.conflict("Not enough space in My files. " + human(used) + " of " + human(owner.quotaBytes) + " is used. Ask IT for more space.");
        }
    }

    public String pathText(Long id) {
        LinkedList<String> parts = new LinkedList<>();
        Node n = nodes.findById(id).orElse(null);
        while (n != null) {
            parts.addFirst(n.fullName());
            n = parentOf(n);
        }
        return String.join(" › ", parts);
    }

    public String principalName(String p) {
        if (p.equals("all")) return "All staff";
        if (p.startsWith("group:")) return GROUP_NAMES.getOrDefault(p.substring(6), p.substring(6) + " team");
        if (p.startsWith("user:")) {
            try {
                return users.findById(Long.parseLong(p.substring(5))).map(u -> u.displayName).orElse(p);
            } catch (NumberFormatException e) {
                return p;
            }
        }
        return p;
    }

    public NodeDto toDto(User user, Node n) {
        Access a = perms.effective(user, n);
        int childCount = n.isFile() || a == Access.NONE ? 0 : (int) nodes.countByParentIdAndDeletedFalse(n.id);
        String modifiedBy = n.modifiedBy == null ? null : users.findById(n.modifiedBy).map(u -> u.displayName).orElse(null);
        String coBy = n.checkedOutBy == null ? null : users.findById(n.checkedOutBy).map(u -> u.displayName).orElse(null);
        boolean mine = perms.ownsPersonal(user, n);
        int sharedCount = mine && n.type != NodeType.ROOT ? (int) shares.findByNodeId(n.id).stream().filter(s -> s.active(Instant.now())).count() : 0;
        String sharedBy = null, shareLevel = null, ownerName = null;
        if (n.space == Space.PERSONAL && !mine) {
            Node root = perms.personalRoot(n);
            ownerName = root == null ? null : users.findById(root.ownerId).map(u -> u.displayName).orElse(null);
            sharedBy = ownerName;
            shareLevel = a == Access.WRITE ? "Can edit" : a == Access.READ ? "Can view" : null;
        }
        return new NodeDto(n.id, n.parentId, n.type.name(), n.space.name(), n.name, n.ext, n.fullName(),
                n.sizeBytes, n.modifiedAt, modifiedBy, a.name(), childCount, n.signed, n.locked, n.stamp, coBy,
                n.checkedOutBy != null && n.checkedOutBy.equals(user.id), n.version, sharedCount, sharedBy, shareLevel, mine, ownerName);
    }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
        return (v >= 10 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v)) + " " + u[i];
    }

    /** Splits "Report.final.PDF" into {"Report.final", "pdf"}. */
    public static String[] splitName(String originalName) {
        String fileName = originalName == null ? "Untitled" : originalName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            return new String[]{fileName.substring(0, dot), fileName.substring(dot + 1).toLowerCase(Locale.ROOT)};
        }
        return new String[]{fileName, null};
    }

    public String cleanName(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) throw ApiException.badRequest("Please type a name.");
        if (n.equals(".") || n.equals("..")) throw ApiException.badRequest("That name isn’t allowed.");
        if (BAD_CHARS.matcher(n).find()) throw ApiException.badRequest("Names can’t contain \\ / : * ? \" < > |");
        if (n.length() > 200) throw ApiException.badRequest("That name is too long (200 characters at most).");
        return n;
    }

    public String uniqueName(Long parentId, String base, String ext) {
        Set<String> taken = new HashSet<>();
        for (Node s : nodes.findByParentIdAndDeletedFalse(parentId)) taken.add(s.fullName().toLowerCase(Locale.ROOT));
        String name = base;
        int i = 2;
        while (taken.contains((ext == null ? name : name + "." + ext).toLowerCase(Locale.ROOT))) name = base + " (" + i++ + ")";
        return name;
    }

    private Node newNode(Node parent, NodeType type, String name, String ext, User by) {
        Node f = new Node();
        f.parentId = parent.id;
        f.type = type;
        f.space = parent.space;
        f.ownerId = by.id;
        f.name = name;
        f.ext = ext;
        f.createdAt = f.modifiedAt = Instant.now();
        f.modifiedBy = by.id;
        return f;
    }

    private void saveVersion(Node f, User by, String note) {
        FileVersion v = new FileVersion();
        v.nodeId = f.id;
        v.number = f.version;
        v.blobKey = f.blobKey;
        v.ext = f.ext;
        v.sizeBytes = f.sizeBytes;
        v.createdAt = Instant.now();
        v.createdBy = by.id;
        v.note = note == null ? null : (note.length() > 300 ? note.substring(0, 300) : note);
        versions.save(v);
    }

    private Node copyTree(Node src, Node target, User by, boolean top) {
        Node c = newNode(target, src.type, top ? uniqueName(target.id, src.name, src.ext) : src.name, src.ext, by);
        c.sizeBytes = src.sizeBytes;
        c.blobKey = src.blobKey;
        c.signed = src.signed;
        c.locked = src.locked;
        c.stamp = src.stamp;
        c.version = 1;
        nodes.save(c);
        if (src.isFile()) saveVersion(c, by, "Copied from “" + src.fullName() + "”");
        else for (Node k : nodes.findByParentIdAndDeletedFalse(src.id)) copyTree(k, c, by, false);
        return c;
    }

    private long treeSize(Node n) {
        if (n.isFile()) return n.sizeBytes;
        long total = 0;
        for (Node k : nodes.findByParentIdAndDeletedFalse(n.id)) total += treeSize(k);
        return total;
    }

    private void setSpace(Node n, Space space) {
        n.space = space;
        nodes.save(n);
        for (Node k : nodes.findByParentIdAndDeletedFalse(n.id)) setSpace(k, space);
    }

    private boolean isInside(Node folder, Node possibleAncestor) {
        Node n = folder;
        while (n != null) {
            if (n.id.equals(possibleAncestor.id)) return true;
            n = parentOf(n);
        }
        return false;
    }

    private void markDeleted(Node n, Instant when, Long by) {
        n.deleted = true;
        n.deletedAt = when;
        n.deletedBy = by;
        nodes.save(n);
        for (Node c : nodes.findByParentIdAndDeletedFalse(n.id)) markDeleted(c, when, by);
    }

    private void restoreTree(Node n, Instant when) {
        n.deleted = false;
        n.deletedAt = null;
        n.deletedBy = null;
        nodes.save(n);
        for (Node c : nodes.findByParentId(n.id)) {
            if (c.deleted && when != null && when.equals(c.deletedAt)) restoreTree(c, when);
        }
    }

    private Node require(Long id) {
        if (id == null) throw ApiException.notFound("This item");
        Node n = nodes.findById(id).orElseThrow(() -> ApiException.notFound("This item"));
        if (n.deleted) throw ApiException.notFound("“" + n.fullName() + "”");
        return n;
    }

    private Node parentOf(Node n) {
        return n.parentId == null ? null : nodes.findById(n.parentId).orElse(null);
    }

    private void countInside(Long id, int[] counts, int depth) {
        if (depth > 30) return;
        for (Node c : nodes.findByParentIdAndDeletedFalse(id)) {
            if (c.isFile()) counts[0]++;
            else {
                counts[1]++;
                countInside(c.id, counts, depth + 1);
            }
        }
    }

    public record FileContent(String fileName, Path path, long size) { }
}
