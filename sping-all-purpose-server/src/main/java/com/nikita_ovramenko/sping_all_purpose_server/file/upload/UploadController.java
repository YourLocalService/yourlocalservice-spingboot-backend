package com.nikita_ovramenko.sping_all_purpose_server.file.upload;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nikita_ovramenko.sping_all_purpose_server.file.FileService;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization;
import com.nikita_ovramenko.sping_all_purpose_server.organization.service.OrganizationLookup;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * Mints a presigned PUT for one quote photo.
 *
 * <p>Replaces GET /api/upload/{name}, which signed whatever key the caller asked for --
 * so any client could overwrite any object in the bucket, including another
 * organization's. Here the key is <em>constructed</em>: the prefix comes from the path
 * slug this method has already resolved, and uniqueness from a server-generated UUID.
 * A caller controls nothing but the file name, and only after sanitising.
 *
 * <p>A POST body also sidesteps the other problem with the old endpoint: keys contain
 * slashes, and Tomcat rejects %2F inside a path segment with a 400.
 *
 * <p>Keys are shaped {@code orgs/<slug>/quotes/<yyyy>/<MM>/<dd>/<uuid>-<name>}. The
 * "quotes" segment is what makes a prefix-scoped S3 lifecycle rule safe -- a rule on
 * {@code orgs/tcs/quotes/} expires quote photos without touching anything else the org
 * keeps in the bucket. The zero-padded date sorts chronologically in the console.
 */
@RestController
@RequestMapping("/api/orgs/{slug}/uploads")
public class UploadController {

    private static final int MAX_NAME_LENGTH = 100;

    /**
     * UTC deliberately: deterministic, no DST edges, and consistent across orgs spanning
     * several countries. The cost is an off-by-one -- a Toronto quote submitted at 8pm
     * EDT files under the next day. That is fine because quote.created_at remains the
     * authoritative timestamp and real lookups go through SQL; this prefix is for coarse
     * browsing and lifecycle rules. Per-org timezones would be a column, not a constant.
     */
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileService fileService;
    private final OrganizationLookup organizationLookup;

    public UploadController(FileService fileService, OrganizationLookup organizationLookup) {
        this.fileService = fileService;
        this.organizationLookup = organizationLookup;
    }

    @PostMapping
    public UploadTarget create(@PathVariable String slug, @Valid @RequestBody UploadRequest request) {
        // Throws (404) on an unknown or inactive org, and gives back the canonical slug
        // rather than whatever casing the caller used.
        Organization organization = organizationLookup.requireBySlug(slug);

        String key = "orgs/%s/quotes/%s/%s-%s".formatted(
                organization.getSlug(),
                LocalDate.now(ZoneOffset.UTC).format(DATE_PATH),
                UUID.randomUUID(),
                sanitize(request.fileName()));

        return new UploadTarget(key, fileService.createPresignedUrl(key, null));
    }

    /**
     * Reduces a client-supplied name to something safe to embed in an S3 key.
     *
     * <p>Notably strips "/", so a name can never introduce its own path segment, and
     * leading dots, so it cannot look like a traversal. Truncation keeps the tail so the
     * extension survives.
     */
    static String sanitize(String fileName) {
        String cleaned = fileName
                .replaceAll("[^\\p{L}\\p{N}._-]", "-")
                // Collapse runs of dots anywhere, not just leading: S3 keys are opaque
                // strings so ".." is harmless there, but these names end up in file
                // systems and download dialogs where it is not.
                .replaceAll("\\.{2,}", ".")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.\\-]+", "");

        if (cleaned.isBlank()) {
            return "upload";
        }
        return cleaned.length() > MAX_NAME_LENGTH
                ? cleaned.substring(cleaned.length() - MAX_NAME_LENGTH)
                : cleaned;
    }
}
