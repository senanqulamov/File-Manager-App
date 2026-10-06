package com.pmis.docket.server.web;

import com.pmis.docket.server.auth.AuthInterceptor;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.service.NodeService;
import com.pmis.docket.server.web.Dto.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class NodeController {
    private static final String U = AuthInterceptor.USER_ATTR;
    private static final String C = AuthInterceptor.COMPUTER_ATTR;
    private final NodeService service;

    public NodeController(NodeService service) {
        this.service = service;
    }

    @GetMapping("/roots")
    public RootsDto roots(@RequestAttribute(U) User user) { return service.roots(user); }

    @GetMapping("/shared-with-me")
    public List<NodeDto> sharedWithMe(@RequestAttribute(U) User user) { return service.sharedWithMe(user); }

    @GetMapping("/recent")
    public List<NodeDto> recent(@RequestAttribute(U) User user) { return service.recent(user); }

    @GetMapping("/people")
    public List<PersonDto> people(@RequestAttribute(U) User user) { return service.people(user); }

    @GetMapping("/nodes/{id}")
    public NodeDto get(@RequestAttribute(U) User user, @PathVariable Long id) { return service.get(user, id); }

    @GetMapping("/nodes/{id}/children")
    public List<NodeDto> children(@RequestAttribute(U) User user, @PathVariable Long id) { return service.children(user, id); }

    @GetMapping("/nodes/{id}/path")
    public List<PathItemDto> path(@RequestAttribute(U) User user, @PathVariable Long id) { return service.path(user, id); }

    @GetMapping("/nodes/{id}/properties")
    public PropertiesDto properties(@RequestAttribute(U) User user, @PathVariable Long id) { return service.properties(user, id); }

    @GetMapping("/nodes/{id}/activity")
    public List<ActivityDto> activity(@RequestAttribute(U) User user, @PathVariable Long id) { return service.activity(user, id); }

    @PostMapping("/nodes/{id}/folders")
    public NodeDto createFolder(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                                @PathVariable Long id, @RequestBody NameRequest req) {
        return service.createFolder(user, computer, id, req.name());
    }

    @PostMapping(value = "/nodes/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NodeDto upload(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                          @PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return service.upload(user, computer, id, file.getOriginalFilename(), in, file.getSize());
        }
    }

    @GetMapping("/nodes/{id}/content")
    public ResponseEntity<Resource> content(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                                            @PathVariable Long id, @RequestParam(defaultValue = "open") String mode,
                                            @RequestParam(required = false) Integer version) {
        NodeService.FileContent fc = service.content(user, computer, id, "download".equals(mode), version);
        MediaType type = MediaTypeFactory.getMediaType(fc.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).contentLength(fc.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fc.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(fc.path()));
    }

    @PatchMapping("/nodes/{id}")
    public NodeDto rename(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                          @PathVariable Long id, @RequestBody NameRequest req) {
        return service.rename(user, computer, id, req.name());
    }

    @DeleteMapping("/nodes/{id}")
    public void delete(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        service.delete(user, computer, id);
    }

    @PostMapping("/nodes/{id}/restore")
    public NodeDto restore(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return service.restore(user, computer, id);
    }

    @PostMapping("/nodes/{id}/copy")
    public NodeDto copy(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                        @PathVariable Long id, @RequestBody TargetRequest req) {
        return service.copy(user, computer, id, req.targetId());
    }

    @PostMapping("/nodes/{id}/move")
    public NodeDto move(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                        @PathVariable Long id, @RequestBody TargetRequest req) {
        return service.move(user, computer, id, req.targetId());
    }

    // ---- check-out and versions ----

    @PostMapping("/nodes/{id}/checkout")
    public NodeDto checkout(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return service.checkout(user, computer, id);
    }

    @PostMapping(value = "/nodes/{id}/checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NodeDto checkin(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                           @PathVariable Long id, @RequestParam(value = "note", required = false) String note,
                           @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return service.checkin(user, computer, id, note, null, null, 0);
        try (InputStream in = file.getInputStream()) {
            return service.checkin(user, computer, id, note, file.getOriginalFilename(), in, file.getSize());
        }
    }

    @PostMapping("/nodes/{id}/discard-checkout")
    public NodeDto discard(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return service.discardCheckout(user, computer, id);
    }

    @GetMapping("/nodes/{id}/versions")
    public List<VersionDto> versions(@RequestAttribute(U) User user, @PathVariable Long id) { return service.versions(user, id); }

    @PostMapping("/nodes/{id}/versions/{number}/restore")
    public NodeDto restoreVersion(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                                  @PathVariable Long id, @PathVariable int number) {
        return service.restoreVersion(user, computer, id, number);
    }

    // ---- sharing and access ----

    @GetMapping("/nodes/{id}/shares")
    public List<ShareDto> shares(@RequestAttribute(U) User user, @PathVariable Long id) { return service.shares(user, id); }

    @PostMapping("/nodes/{id}/shares")
    public List<ShareDto> share(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                                @PathVariable Long id, @RequestBody ShareRequest req) {
        return service.share(user, computer, id, req.userIds(), req.canEdit(), req.days());
    }

    @DeleteMapping("/nodes/{id}/shares/{shareId}")
    public void unshare(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                        @PathVariable Long id, @PathVariable Long shareId) {
        service.unshare(user, computer, id, shareId);
    }

    @PostMapping("/nodes/{id}/access-requests")
    public void requestAccess(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                              @PathVariable Long id, @RequestBody AccessRequestBody req) {
        service.requestAccess(user, computer, id, req.level(), req.note());
    }
}
