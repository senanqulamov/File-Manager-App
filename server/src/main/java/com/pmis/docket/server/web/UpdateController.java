package com.pmis.docket.server.web;

import com.pmis.docket.server.service.UpdateService;
import com.pmis.docket.server.web.Dto.UpdateInfo;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;

/** Public (no sign-in needed) so a PC can update even before signing in. */
@RestController
@RequestMapping("/api/updates")
public class UpdateController {
    private final UpdateService updates;

    public UpdateController(UpdateService updates) {
        this.updates = updates;
    }

    @GetMapping("/latest")
    public UpdateInfo latest(@RequestParam(defaultValue = "0") String current) { return updates.latest(current); }

    @GetMapping("/download")
    public ResponseEntity<Resource> download() {
        if (!Files.isRegularFile(updates.installer())) throw ApiException.notFound("The update");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("PMIS-Docket-Setup.exe").build().toString())
                .body(new FileSystemResource(updates.installer()));
    }
}
