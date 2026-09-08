package com.nikita_ovramenko.sping_all_purpose_server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nikita_ovramenko.sping_all_purpose_server.file.FileService;
import com.nikita_ovramenko.sping_all_purpose_server.file.upload.UploadController;
import com.nikita_ovramenko.sping_all_purpose_server.file.upload.UploadRequest;
import com.nikita_ovramenko.sping_all_purpose_server.file.upload.UploadTarget;
import com.nikita_ovramenko.sping_all_purpose_server.organization.exception.OrganizationNotFoundException;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization;
import com.nikita_ovramenko.sping_all_purpose_server.organization.service.OrganizationLookup;

/** No Spring context needed -- the interesting logic is key construction. */
class UploadControllerTest {

    /**
     * The full key shape, asserted rather than loosened -- the shape IS the security
     * property. The trailing [^/]+ is the important part: it proves a client-supplied
     * file name cannot introduce a path segment of its own.
     */
    private static final Pattern KEY_SHAPE = Pattern.compile(
            "^orgs/tcs/quotes/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}-[^/]+$");

    private OrganizationLookup organizationLookup;
    private UploadController controller;

    @BeforeEach
    void setUp() {
        FileService fileService = mock(FileService.class);
        given(fileService.createPresignedUrl(anyString(), any()))
                .willAnswer(call -> "https://bucket.s3/" + call.getArgument(0));

        organizationLookup = mock(OrganizationLookup.class);
        Organization tcs = new Organization();
        tcs.setSlug("tcs");
        given(organizationLookup.requireBySlug(anyString())).willReturn(tcs);

        controller = new UploadController(fileService, organizationLookup);
    }

    private String keyFor(String fileName) {
        return controller.create("tcs", new UploadRequest(fileName)).key();
    }

    @Test
    void keyIsScopedToTheOrganizationAndUnique() {
        UploadTarget first = controller.create("tcs", new UploadRequest("photo.jpg"));
        UploadTarget second = controller.create("tcs", new UploadRequest("photo.jpg"));

        assertThat(first.key()).matches(KEY_SHAPE).endsWith("-photo.jpg");
        assertThat(first.key()).isNotEqualTo(second.key());
        assertThat(first.url()).isEqualTo("https://bucket.s3/" + first.key());
    }

    /** Zero-padded and UTC, so the console lists objects chronologically. */
    @Test
    void keyIsFiledUnderTodaysUtcDate() {
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        assertThat(keyFor("photo.jpg")).contains("/quotes/" + today + "/");
    }

    /**
     * The "quotes" segment exists so an S3 lifecycle rule can target quote photos alone,
     * without sweeping up anything else the org stores under its prefix.
     */
    @Test
    void keyCarriesTheEntitySegmentALifecycleRuleTargets() {
        assertThat(keyFor("photo.jpg")).startsWith("orgs/tcs/quotes/");
    }

    /**
     * The whole point of the change: the old endpoint signed whatever key arrived, so a
     * modified client could write into another organization's prefix.
     */
    @Test
    void clientCannotEscapeItsOwnPrefix() {
        // A hostile name may survive as text, but never as structure: what matters is
        // that it cannot introduce a path segment. Every key is exactly
        // orgs/<slug>/quotes/<date>/<uuid>-<name>, which KEY_SHAPE asserts in full.
        for (String hostile : new String[] {
                "../../orgs/yourlocalhandyman/steal.jpg",
                "a/b/c.jpg",
                "/absolute.jpg",
                "..%2F..%2Fetc.jpg" }) {

            String key = keyFor(hostile);
            assertThat(key).as("hostile name %s", hostile).matches(KEY_SHAPE).doesNotContain("..");
        }
    }

    /** The canonical slug is used, not the caller's casing. */
    @Test
    void keyUsesTheResolvedSlugNotThePathAsTyped() {
        assertThat(controller.create("TCS", new UploadRequest("x.png")).key())
                .matches(KEY_SHAPE);
    }

    @Test
    void unknownOrganizationIsRejectedBeforeAnyKeyIsMinted() {
        willThrow(new OrganizationNotFoundException("nope"))
                .given(organizationLookup).requireBySlug("nope");

        assertThatThrownBy(() -> controller.create("nope", new UploadRequest("x.jpg")))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void fileNameIsSanitisedButKeepsItsExtension() {
        assertThat(keyFor("my holiday photo (1).JPG")).endsWith("-my-holiday-photo-1-.JPG");
        assertThat(keyFor("...")).endsWith("-upload");
        assertThat(keyFor("réservé.png")).endsWith("-réservé.png");
    }

    /** Truncation keeps the tail so the extension survives a very long name. */
    @Test
    void veryLongFileNameIsTruncatedFromTheFront() {
        String key = keyFor("x".repeat(300) + ".jpg");

        assertThat(key).endsWith(".jpg");
        assertThat(key.substring(key.lastIndexOf('-') + 1)).hasSize(100);
    }
}
