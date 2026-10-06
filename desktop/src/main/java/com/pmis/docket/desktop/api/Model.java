package com.pmis.docket.desktop.api;

import java.time.Instant;
import java.util.List;

/** JSON shapes returned by the PMIS Docket server. */
public final class Model {
    private Model() { }

    public record UserInfo(Long id, String login, String displayName, String initials, String department, String role,
                           boolean mustChangePassword) {
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }

    public record LoginResponse(String token, UserInfo user) { }

    public record NodeInfo(Long id, Long parentId, String type, String space, String name, String ext, String fullName,
                           long sizeBytes, Instant modifiedAt, String modifiedByName, String access, int childCount,
                           boolean signed, boolean locked, String stamp, String checkedOutByName, boolean checkedOutByMe,
                           int version, int sharedCount, String sharedByName, String shareLevel, boolean mine, String ownerName) {
        public boolean isFile() { return "FILE".equals(type); }

        public boolean isRoot() { return "ROOT".equals(type); }

        public boolean isPersonal() { return "PERSONAL".equals(space); }

        /** In someone else's My files (reached through "Shared with me"). */
        public boolean isSharedToMe() { return isPersonal() && !mine; }

        public boolean noAccess() { return "NONE".equals(access); }

        public boolean readOnly() { return "READ".equals(access); }

        public boolean canWrite() { return "WRITE".equals(access) || "FULL".equals(access); }

        public boolean checkedOutByOther() { return checkedOutByName != null && !checkedOutByMe; }

        public String accessLabel() {
            return switch (access == null ? "" : access) {
                case "FULL" -> "Full control";
                case "WRITE" -> "Read & write";
                case "READ" -> "Read only";
                default -> "No access";
            };
        }
    }

    public record Roots(NodeInfo personal, NodeInfo company, int sharedWithMe, boolean admin) { }

    public record PathItem(Long id, String name) { }

    public record AccessRow(String who, String level) { }

    public record Properties(NodeInfo node, String location, String contains, String owner, List<AccessRow> access) { }

    public record Activity(Instant at, String user, String action, String computer) { }

    public record Version(int number, String byName, Instant at, String note, long sizeBytes, boolean current) { }

    public record ShareInfo(Long id, Long userId, String userName, String initials, String level, Instant expiresAt) { }

    public record Person(Long id, String name, String initials, String department) { }

    public record Conversions(List<String> formats, String note) { }

    public record ActionResult(NodeInfo node, String message) { }

    public record PreviewInfo(String kind, int pages, String message) { }

    public record ArchiveEntry(String name, long size, boolean folder) { }

    // ---- admin ----
    public record AdminUser(Long id, String login, String displayName, String initials, String department, String groupKey,
                            String role, boolean active, long usedBytes, long quotaBytes, Instant lastSignIn, String lastComputer) { }

    public record TempPassword(AdminUser user, String temporaryPassword) { }

    public record Folder(Long id, String name, int depth, boolean hasOwnPermissions) { }

    public record AclLine(String principal, String who, String access, boolean fixed) { }

    public record Acl(Long nodeId, String name, String path, boolean inherited, String inheritedFrom, List<AclLine> lines, List<AclLine> addable) { }

    public record RequestInfo(Long id, String userName, Long nodeId, String folder, String level, String note, Instant at, String status) { }

    public record AuditEntry(Long id, Instant at, String user, String action, String kind, String item, String computer) { }

    public record ServerStats(long totalBytes, long freeBytes, long filesBytes, long recycleBytes, long users, long activeToday,
                              long checkedOut, long pendingRequests, boolean libreOffice, boolean ffmpeg, String storageRoot) { }

    public record RecycleItem(Long id, String name, String type, String location, String deletedBy, Instant deletedAt, long sizeBytes) { }

    public record UpdateInfo(String version, boolean available, String notes) { }

    public record Message(String message) { }
}
