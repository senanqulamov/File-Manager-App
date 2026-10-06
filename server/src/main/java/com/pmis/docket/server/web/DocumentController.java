package com.pmis.docket.server.web;

import com.pmis.docket.server.auth.AuthInterceptor;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.service.DocumentService;
import com.pmis.docket.server.service.PreviewService;
import com.pmis.docket.server.web.Dto.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/nodes/{id}")
public class DocumentController {
    private static final String U = AuthInterceptor.USER_ATTR;
    private static final String C = AuthInterceptor.COMPUTER_ATTR;
    private final DocumentService docs;
    private final PreviewService preview;

    public DocumentController(DocumentService docs, PreviewService preview) {
        this.docs = docs;
        this.preview = preview;
    }

    @GetMapping("/preview")
    public PreviewInfo previewInfo(@RequestAttribute(U) User user, @PathVariable Long id) { return preview.info(user, id); }

    @GetMapping("/preview/page/{page}")
    public ResponseEntity<byte[]> page(@RequestAttribute(U) User user, @PathVariable Long id, @PathVariable int page,
                                       @RequestParam(defaultValue = "110") int dpi) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate()).body(preview.page(user, id, page, dpi));
    }

    @GetMapping("/preview/image")
    public ResponseEntity<byte[]> image(@RequestAttribute(U) User user, @PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(preview.image(user, id));
    }

    @GetMapping("/archive")
    public List<ArchiveEntryDto> archive(@RequestAttribute(U) User user, @PathVariable Long id) { return preview.entries(user, id); }

    @PostMapping("/extract")
    public NodeDto extract(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer, @PathVariable Long id) {
        return preview.extract(user, computer, id);
    }

    @GetMapping("/conversions")
    public ConversionsDto conversions(@RequestAttribute(U) User user, @PathVariable Long id) { return docs.conversions(user, id); }

    @PostMapping("/convert")
    public ActionResult convert(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                                @PathVariable Long id, @RequestBody ConvertRequest req) {
        return docs.convert(user, computer, id, req.format());
    }

    @PostMapping("/sign")
    public ActionResult sign(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                             @PathVariable Long id, @RequestBody SignRequest req) {
        return docs.sign(user, computer, id, req.mode(), req.imagePngBase64(), req.placement());
    }

    @PostMapping("/lock")
    public ActionResult lock(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                             @PathVariable Long id, @RequestBody LockRequest req) {
        return docs.lock(user, computer, id, req.password(), req.allowPrint(), req.allowCopy());
    }

    @PostMapping("/unlock")
    public ActionResult unlock(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                               @PathVariable Long id, @RequestBody UnlockRequest req) {
        return docs.unlock(user, computer, id, req.password());
    }

    @PostMapping("/stamp")
    public ActionResult stamp(@RequestAttribute(U) User user, @RequestAttribute(value = C, required = false) String computer,
                              @PathVariable Long id, @RequestBody StampRequest req) {
        return docs.stamp(user, computer, id, req.text(), req.color());
    }
}
