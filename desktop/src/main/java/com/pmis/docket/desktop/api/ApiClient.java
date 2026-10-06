package com.pmis.docket.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

/** Talks to the PMIS Docket server over HTTP(S). All methods block: call them off the JavaFX thread. */
public class ApiClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private volatile String baseUrl;
    private volatile String token;

    public ApiClient(String baseUrl) {
        setBaseUrl(baseUrl);
    }

    public void setBaseUrl(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.baseUrl = u;
    }

    public String baseUrl() { return baseUrl; }

    // ================================================================== sign-in

    public boolean health() {
        try {
            HttpResponse<String> r = http.send(request("/api/health").timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public Model.LoginResponse login(String login, String password, String computer) {
        Model.LoginResponse r = post("/api/auth/login", Map.of("login", login, "password", password, "computer", computer), new TypeReference<>() { });
        this.token = r.token();
        return r;
    }

    public Model.UserInfo changePassword(String oldPassword, String newPassword) {
        return post("/api/auth/password", Map.of("oldPassword", oldPassword, "newPassword", newPassword), new TypeReference<>() { });
    }

    public void logout() {
        try {
            send(request("/api/auth/logout").POST(BodyPublishers.noBody()), null);
        } catch (ApiException ignored) {
            // Signing out locally is enough if the server is gone.
        }
        token = null;
    }

    // ================================================================== browsing

    public Model.Roots roots() { return get("/api/roots", new TypeReference<>() { }); }

    public List<Model.NodeInfo> sharedWithMe() { return get("/api/shared-with-me", new TypeReference<>() { }); }

    public List<Model.NodeInfo> recent() { return get("/api/recent", new TypeReference<>() { }); }

    public List<Model.Person> people() { return get("/api/people", new TypeReference<>() { }); }

    public Model.NodeInfo node(long id) { return get("/api/nodes/" + id, new TypeReference<>() { }); }

    public List<Model.NodeInfo> children(long id) { return get("/api/nodes/" + id + "/children", new TypeReference<>() { }); }

    public List<Model.PathItem> path(long id) { return get("/api/nodes/" + id + "/path", new TypeReference<>() { }); }

    public Model.Properties properties(long id) { return get("/api/nodes/" + id + "/properties", new TypeReference<>() { }); }

    public List<Model.Activity> activity(long id) { return get("/api/nodes/" + id + "/activity", new TypeReference<>() { }); }

    // ================================================================== changes

    public Model.NodeInfo createFolder(long parentId, String name) {
        return post("/api/nodes/" + parentId + "/folders", Map.of("name", name), new TypeReference<>() { });
    }

    public Model.NodeInfo rename(long id, String name) {
        return send(jsonRequest("/api/nodes/" + id).method("PATCH", jsonBody(Map.of("name", name))), new TypeReference<>() { });
    }

    public void delete(long id) { send(request("/api/nodes/" + id).DELETE(), null); }

    public Model.NodeInfo restore(long id) { return post("/api/nodes/" + id + "/restore", Map.of(), new TypeReference<>() { }); }

    public Model.NodeInfo copy(long id, long targetId) { return post("/api/nodes/" + id + "/copy", Map.of("targetId", targetId), new TypeReference<>() { }); }

    public Model.NodeInfo move(long id, long targetId) { return post("/api/nodes/" + id + "/move", Map.of("targetId", targetId), new TypeReference<>() { }); }

    public Model.NodeInfo upload(long parentId, Path file) {
        return multipart("/api/nodes/" + parentId + "/files", Map.of(), file, new TypeReference<>() { });
    }

    // ================================================================== check-out and versions

    public Model.NodeInfo checkout(long id) { return post("/api/nodes/" + id + "/checkout", Map.of(), new TypeReference<>() { }); }

    public Model.NodeInfo checkin(long id, String note, Path changedFile) {
        return multipart("/api/nodes/" + id + "/checkin", Map.of("note", note == null ? "" : note), changedFile, new TypeReference<>() { });
    }

    public Model.NodeInfo discardCheckout(long id) { return post("/api/nodes/" + id + "/discard-checkout", Map.of(), new TypeReference<>() { }); }

    public List<Model.Version> versions(long id) { return get("/api/nodes/" + id + "/versions", new TypeReference<>() { }); }

    public Model.NodeInfo restoreVersion(long id, int number) {
        return post("/api/nodes/" + id + "/versions/" + number + "/restore", Map.of(), new TypeReference<>() { });
    }

    // ================================================================== sharing and access

    public List<Model.ShareInfo> shares(long id) { return get("/api/nodes/" + id + "/shares", new TypeReference<>() { }); }

    public List<Model.ShareInfo> share(long id, List<Long> userIds, boolean canEdit, Integer days) {
        Map<String, Object> body = new HashMap<>();
        body.put("userIds", userIds);
        body.put("canEdit", canEdit);
        body.put("days", days);
        return post("/api/nodes/" + id + "/shares", body, new TypeReference<>() { });
    }

    public void unshare(long id, long shareId) { send(request("/api/nodes/" + id + "/shares/" + shareId).DELETE(), null); }

    public void requestAccess(long id, String level, String note) {
        send(jsonRequest("/api/nodes/" + id + "/access-requests").POST(jsonBody(Map.of("level", level, "note", note == null ? "" : note))), null);
    }

    // ================================================================== preview and documents

    public Model.PreviewInfo previewInfo(long id) { return get("/api/nodes/" + id + "/preview", new TypeReference<>() { }); }

    public byte[] previewPage(long id, int page, int dpi) { return bytes("/api/nodes/" + id + "/preview/page/" + page + "?dpi=" + dpi); }

    public byte[] previewImage(long id) { return bytes("/api/nodes/" + id + "/preview/image"); }

    public List<Model.ArchiveEntry> archive(long id) { return get("/api/nodes/" + id + "/archive", new TypeReference<>() { }); }

    public Model.NodeInfo extract(long id) { return post("/api/nodes/" + id + "/extract", Map.of(), new TypeReference<>() { }); }

    public Model.Conversions conversions(long id) { return get("/api/nodes/" + id + "/conversions", new TypeReference<>() { }); }

    public Model.ActionResult convert(long id, String format) {
        return post("/api/nodes/" + id + "/convert", Map.of("format", format), new TypeReference<>() { });
    }

    public Model.ActionResult sign(long id, String mode, String imagePngBase64, String placement) {
        Map<String, Object> body = new HashMap<>();
        body.put("mode", mode);
        body.put("imagePngBase64", imagePngBase64);
        body.put("placement", placement);
        return post("/api/nodes/" + id + "/sign", body, new TypeReference<>() { });
    }

    public Model.ActionResult lock(long id, String password, boolean allowPrint, boolean allowCopy) {
        return post("/api/nodes/" + id + "/lock", Map.of("password", password, "allowPrint", allowPrint, "allowCopy", allowCopy), new TypeReference<>() { });
    }

    public Model.ActionResult stamp(long id, String text, String color) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("color", color);
        return post("/api/nodes/" + id + "/stamp", body, new TypeReference<>() { });
    }

    // ================================================================== admin

    public List<Model.AdminUser> adminUsers() { return get("/api/admin/users", new TypeReference<>() { }); }

    public Model.TempPassword createUser(Map<String, Object> body) { return post("/api/admin/users", body, new TypeReference<>() { }); }

    public Model.AdminUser updateUser(long id, Map<String, Object> body) {
        return send(jsonRequest("/api/admin/users/" + id).method("PATCH", jsonBody(body)), new TypeReference<>() { });
    }

    public Model.TempPassword resetPassword(long id) { return post("/api/admin/users/" + id + "/reset-password", Map.of(), new TypeReference<>() { }); }

    public List<Model.Folder> adminFolders() { return get("/api/admin/folders", new TypeReference<>() { }); }

    public Model.Acl acl(long nodeId) { return get("/api/admin/acl/" + nodeId, new TypeReference<>() { }); }

    public Model.Acl setAcl(long nodeId, String principal, String access) {
        return send(jsonRequest("/api/admin/acl/" + nodeId).PUT(jsonBody(Map.of("principal", principal, "access", access))), new TypeReference<>() { });
    }

    public Model.Acl removeAcl(long nodeId, String principal) {
        return send(request("/api/admin/acl/" + nodeId + "?principal=" + enc(principal)).DELETE(), new TypeReference<>() { });
    }

    public Model.Acl inheritAcl(long nodeId) { return post("/api/admin/acl/" + nodeId + "/inherit", Map.of(), new TypeReference<>() { }); }

    public List<Model.RequestInfo> adminRequests() { return get("/api/admin/requests", new TypeReference<>() { }); }

    public Model.RequestInfo decide(long id, boolean approve) {
        return post("/api/admin/requests/" + id + (approve ? "/approve" : "/deny"), Map.of(), new TypeReference<>() { });
    }

    public List<Model.AuditEntry> audit(String kind, String q) {
        return get("/api/admin/audit?limit=400&kind=" + enc(kind) + "&q=" + enc(q), new TypeReference<>() { });
    }

    public void exportAudit(String kind, String q, Path target) {
        saveTo("/api/admin/audit/export?kind=" + enc(kind) + "&q=" + enc(q), target);
    }

    public Model.ServerStats stats() { return get("/api/admin/stats", new TypeReference<>() { }); }

    public List<Model.RecycleItem> recycleBin() { return get("/api/admin/recycle-bin", new TypeReference<>() { }); }

    public Model.NodeInfo adminRestore(long id) { return post("/api/admin/recycle-bin/" + id + "/restore", Map.of(), new TypeReference<>() { }); }

    public Model.Message purge() { return post("/api/admin/recycle-bin/purge", Map.of(), new TypeReference<>() { }); }

    public Model.UpdateInfo publishUpdate(String version, String notes, Path installer) {
        return multipart("/api/admin/updates", Map.of("version", version, "notes", notes == null ? "" : notes), installer, new TypeReference<>() { });
    }

    // ================================================================== updates

    public Model.UpdateInfo latestUpdate(String current) {
        return get("/api/updates/latest?current=" + enc(current), new TypeReference<>() { });
    }

    public void downloadUpdate(Path target) { saveTo("/api/updates/download", target); }

    // ================================================================== files

    /** Saves a file's contents (current version, or {@code version}) to {@code target}. */
    public void download(long id, Path target, boolean forDownload) { download(id, target, forDownload, null); }

    public void download(long id, Path target, boolean forDownload, Integer version) {
        saveTo("/api/nodes/" + id + "/content?mode=" + (forDownload ? "download" : "open") + (version == null ? "" : "&version=" + version), target);
    }

    // ================================================================== plumbing

    private <T> T get(String path, TypeReference<T> type) { return send(request(path).header("Accept", "application/json").GET(), type); }

    private <T> T post(String path, Object body, TypeReference<T> type) { return send(jsonRequest(path).POST(jsonBody(body)), type); }

    private byte[] bytes(String path) {
        try {
            HttpResponse<byte[]> r = http.send(request(path).header("Accept", "*/*").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) throw error(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8));
            return r.body();
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException | HttpTimeoutException e) {
            throw unreachable();
        } catch (IOException e) {
            throw new ApiException(0, "Lost connection to the server. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "The request was interrupted.");
        }
    }

    private void saveTo(String path, Path target) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path part = target.resolveSibling(target.getFileName() + ".part");
            HttpResponse<Path> r = http.send(request(path).header("Accept", "*/*").timeout(Duration.ofHours(6)).GET().build(), HttpResponse.BodyHandlers.ofFile(part));
            if (r.statusCode() != 200) {
                String body = Files.readString(part);
                Files.deleteIfExists(part);
                throw error(r.statusCode(), body);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException e) {
            throw unreachable();
        } catch (IOException e) {
            throw new ApiException(0, "The download stopped. Check your connection and try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "The download was interrupted.");
        }
    }

    private <T> T multipart(String path, Map<String, String> fields, Path file, TypeReference<T> type) {
        String boundary = "----PMISDocket" + UUID.randomUUID().toString().replace("-", "");
        List<BodyPublisher> parts = new ArrayList<>();
        for (Map.Entry<String, String> f : fields.entrySet()) {
            parts.add(BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey()
                    + "\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + f.getValue() + "\r\n", StandardCharsets.UTF_8));
        }
        if (file != null) {
            String name = file.getFileName().toString();
            String ascii = name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + ascii
                    + "\"; filename*=UTF-8''" + enc(name) + "\r\nContent-Type: application/octet-stream\r\n\r\n";
            parts.add(BodyPublishers.ofString(head, StandardCharsets.UTF_8));
            try {
                parts.add(BodyPublishers.ofFile(file));
            } catch (IOException e) {
                throw new ApiException(0, "Can’t read “" + name + "” on this computer.");
            }
            parts.add(BodyPublishers.ofString("\r\n"));
        }
        parts.add(BodyPublishers.ofString("--" + boundary + "--\r\n"));
        BodyPublisher body = BodyPublishers.concat(parts.toArray(new BodyPublisher[0]));
        return send(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary).timeout(Duration.ofHours(6)).POST(body), type);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(120));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private HttpRequest.Builder jsonRequest(String path) {
        return request(path).header("Content-Type", "application/json; charset=UTF-8").header("Accept", "application/json");
    }

    private BodyPublisher jsonBody(Object value) {
        try {
            return BodyPublishers.ofString(json.writeValueAsString(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T send(HttpRequest.Builder builder, TypeReference<T> type) {
        try {
            HttpResponse<String> r = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) throw error(r.statusCode(), r.body());
            if (type == null || r.body() == null || r.body().isBlank()) return null;
            return json.readValue(r.body(), type);
        } catch (ApiException e) {
            throw e;
        } catch (ConnectException | HttpTimeoutException e) {
            throw unreachable();
        } catch (IOException e) {
            throw new ApiException(0, "Lost connection to the server. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "The request was interrupted.");
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20"); }

    private ApiException unreachable() {
        return new ApiException(0, "Can’t reach the server at " + baseUrl + ". Check your network or VPN.");
    }

    private ApiException error(int status, String body) {
        String message = null;
        try {
            JsonNode n = json.readTree(body);
            if (n != null && n.hasNonNull("message")) message = n.get("message").asText();
        } catch (Exception ignored) {
            // Not JSON: use a generic message below.
        }
        if (message == null || message.isBlank()) {
            message = switch (status) {
                case 401 -> "Please sign in again.";
                case 403 -> "You don’t have permission to do that.";
                case 404 -> "That item no longer exists.";
                case 413 -> "This file is too large to upload.";
                default -> "The server returned an error (" + status + ").";
            };
        }
        return new ApiException(status, message);
    }
}
