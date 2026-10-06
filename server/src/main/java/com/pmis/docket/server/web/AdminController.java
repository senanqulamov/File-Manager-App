package com.pmis.docket.server.web;

import com.pmis.docket.server.auth.AuthInterceptor;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.service.AdminService;
import com.pmis.docket.server.service.NodeService;
import com.pmis.docket.server.service.UpdateService;
import com.pmis.docket.server.web.Dto.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final String U = AuthInterceptor.USER_ATTR;
    private static final String C = AuthInterceptor.COMPUTER_ATTR;
    private final AdminService admin;
    private final NodeService nodes;
    private final UpdateService updates;

    public AdminController(AdminService admin, NodeService nodes, UpdateService updates) {
        this.admin = admin;
        this.nodes = nodes;
        this.updates = updates;
    }

    @GetMapping("/users")
    public List<AdminUserDto> users(@RequestAttribute(U) User user) { return admin.users(user); }

    @PostMapping("/users")
    public TempPasswordDto create(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @RequestBody UserRequest req) {
        return admin.createUser(user, computer, req);
    }

    @PatchMapping("/users/{id}")
    public AdminUserDto update(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                               @PathVariable Long id, @RequestBody UserRequest req) {
        return admin.updateUser(user, computer, id, req);
    }

    @PostMapping("/users/{id}/reset-password")
    public TempPasswordDto reset(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return admin.resetPassword(user, computer, id);
    }

    @GetMapping("/folders")
    public List<FolderDto> folders(@RequestAttribute(U) User user) { return admin.folders(user); }

    @GetMapping("/acl/{nodeId}")
    public AclDto acl(@RequestAttribute(U) User user, @PathVariable Long nodeId) { return admin.acl(user, nodeId); }

    @PutMapping("/acl/{nodeId}")
    public AclDto setAcl(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                         @PathVariable Long nodeId, @RequestBody AclRequest req) {
        return admin.setAcl(user, computer, nodeId, req.principal(), req.access());
    }

    @DeleteMapping("/acl/{nodeId}")
    public AclDto removeAcl(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                            @PathVariable Long nodeId, @RequestParam String principal) {
        return admin.removeAcl(user, computer, nodeId, principal);
    }

    @PostMapping("/acl/{nodeId}/inherit")
    public AclDto inherit(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long nodeId) {
        return admin.inherit(user, computer, nodeId);
    }

    @GetMapping("/requests")
    public List<RequestDto> requests(@RequestAttribute(U) User user) { return admin.requests(user); }

    @PostMapping("/requests/{id}/approve")
    public RequestDto approve(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return admin.decide(user, computer, id, true);
    }

    @PostMapping("/requests/{id}/deny")
    public RequestDto deny(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return admin.decide(user, computer, id, false);
    }

    @GetMapping("/audit")
    public List<AuditDto> audit(@RequestAttribute(U) User user, @RequestParam(defaultValue = "All") String kind,
                                @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "300") int limit) {
        return admin.audit(user, kind, q, limit);
    }

    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> export(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                                         @RequestParam(defaultValue = "All") String kind, @RequestParam(defaultValue = "") String q) {
        byte[] body = admin.auditCsv(user, computer, kind, q).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-log.csv").build().toString())
                .body(body);
    }

    @GetMapping("/stats")
    public ServerStatsDto stats(@RequestAttribute(U) User user) { return admin.stats(user); }

    @GetMapping("/recycle-bin")
    public List<RecycleDto> recycleBin(@RequestAttribute(U) User user) { return admin.recycleBin(user); }

    @PostMapping("/recycle-bin/{id}/restore")
    public NodeDto restore(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        AdminService.requireAdmin(user);
        return nodes.restore(user, computer, id);
    }

    @PostMapping("/recycle-bin/purge")
    public Map<String, String> purge(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer) {
        return Map.of("message", admin.purge(user, computer));
    }

    @PostMapping(value = "/updates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UpdateInfo publish(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                              @RequestParam("version") String version, @RequestParam(value = "notes", required = false) String notes,
                              @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return updates.publish(user, computer, version, notes, in);
        }
    }
}
