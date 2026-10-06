package com.pmis.docket.server.web;

import java.time.Instant;
import java.util.List;

/** All JSON shapes exchanged with the desktop app. */
public final class Dto {
    private Dto() { }

    public record ApiError(String message) { }

    public record LoginRequest(String login, String password, String computer) { }

    public record LoginResponse(String token, UserDto user) { }

    public record UserDto(Long id, String login, String displayName, String initials, String department, String role,
                          boolean mustChangePassword) { }

    public record PasswordRequest(String oldPassword, String newPassword) { }

    public record NodeDto(
            Long id, Long parentId, String type, String space, String name, String ext, String fullName,
            long sizeBytes, Instant modifiedAt, String modifiedByName, String access, int childCount,
            boolean signed, boolean locked, String stamp, String checkedOutByName, boolean checkedOutByMe,
            int version, int sharedCount, String sharedByName, String shareLevel, boolean mine, String ownerName) { }

    public record RootsDto(NodeDto personal, NodeDto company, int sharedWithMe, boolean admin) { }

    public record PathItemDto(Long id, String name) { }

    public record NameRequest(String name) { }

    public record TargetRequest(Long targetId) { }

    public record AccessRowDto(String who, String level) { }

    public record PropertiesDto(NodeDto node, String location, String contains, String owner, List<AccessRowDto> access) { }

    public record ActivityDto(Instant at, String user, String action, String computer) { }

    public record VersionDto(int number, String byName, Instant at, String note, long sizeBytes, boolean current) { }

    public record CheckinRequest(String note) { }

    public record ShareDto(Long id, Long userId, String userName, String initials, String level, Instant expiresAt) { }

    public record ShareRequest(List<Long> userIds, boolean canEdit, Integer days) { }

    public record PersonDto(Long id, String name, String initials, String department) { }

    public record AccessRequestBody(String level, String note) { }

    // ---- documents ----
    public record ConversionsDto(List<String> formats, String note) { }

    public record ConvertRequest(String format) { }

    public record SignRequest(String mode, String imagePngBase64, String placement) { }

    public record LockRequest(String password, boolean allowPrint, boolean allowCopy) { }

    public record StampRequest(String text, String color) { }

    public record ActionResult(NodeDto node, String message) { }

    public record PreviewInfo(String kind, int pages, String message) { }

    public record ArchiveEntryDto(String name, long size, boolean folder) { }

    // ---- admin ----
    public record AdminUserDto(Long id, String login, String displayName, String initials, String department, String groupKey,
                               String role, boolean active, long usedBytes, long quotaBytes, Instant lastSignIn, String lastComputer) { }

    public record UserRequest(String login, String displayName, String department, String groupKey, String role,
                              Long quotaGb, Boolean active) { }

    public record TempPasswordDto(AdminUserDto user, String temporaryPassword) { }

    public record FolderDto(Long id, String name, int depth, boolean hasOwnPermissions) { }

    public record AclLineDto(String principal, String who, String access, boolean fixed) { }

    public record AclDto(Long nodeId, String name, String path, boolean inherited, String inheritedFrom, List<AclLineDto> lines,
                         List<AclLineDto> addable) { }

    public record AclRequest(String principal, String access) { }

    public record RequestDto(Long id, String userName, Long nodeId, String folder, String level, String note, Instant at, String status) { }

    public record AuditDto(Long id, Instant at, String user, String action, String kind, String item, String computer) { }

    public record ServerStatsDto(long totalBytes, long freeBytes, long filesBytes, long recycleBytes, long users, long activeToday,
                                 long checkedOut, long pendingRequests, boolean libreOffice, boolean ffmpeg, String storageRoot) { }

    public record RecycleDto(Long id, String name, String type, String location, String deletedBy, Instant deletedAt, long sizeBytes) { }

    public record UpdateInfo(String version, boolean available, String notes) { }
}
