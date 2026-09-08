package com.nikita_ovramenko.sping_all_purpose_server.file;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * SigV4's hard ceiling. There is no longer option and no "never expires" -- a URL
     * signed for more than this is rejected by S3. Anything needing permanent access
     * has to be attached to the mail instead of linked.
     */
    private static final Duration GET_LINK_TTL = Duration.ofDays(7);

    /** Long enough for a large photo over a slow mobile connection. */
    private static final Duration PUT_LINK_TTL = Duration.ofMinutes(10);

    @Value("${spring.app.aws_bucket_name}")
    private String bucketName;

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    public FileService(S3Presigner s3Presigner, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    /** An object pulled out of S3, ready to attach. */
    public record StoredObject(String key, byte[] bytes, String contentType) {
    }

    /* Create a presigned URL to use in a subsequent PUT request */
    public String createPresignedUrl(String keyName, Map<String, String> metadata) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .metadata(metadata)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    // Was 60 seconds, which is not enough for a phone photo on cellular --
                    // a plausible cause of uploads that failed with no visible error.
                    .signatureDuration(PUT_LINK_TTL)
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            return presignedRequest.url().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create presigned PUT URL for key=" + keyName, e);
        }
    }

    public String createPresignedGetLink(String key) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(GET_LINK_TTL)
                    .getObjectRequest(objectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            return presignedRequest.url().toString();

        } catch (Exception e) {
            // Do not fail the caller (an email send) over one unreachable object, but never
            // return a blank string silently -- that renders as "images: [, , ]" in the mail.
            log.error("Failed to create presigned GET URL for key={}", key, e);
            return "<link unavailable for " + key + ">";
        }
    }

    public List<String> createPresignedGetLinks(List<String> keys) {
        List<String> links = new ArrayList<>();

        for (String key : keys) {
            links.add(createPresignedGetLink(key));
        }

        return links;
    }

    /**
     * An object's size, without downloading it.
     *
     * <p>Used to decide what fits in an email's attachment budget before spending
     * bandwidth and heap on the bytes.
     */
    public OptionalLong contentLength(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return OptionalLong.of(head.contentLength());
        } catch (Exception e) {
            log.error("Failed to read metadata for s3 object key={}", key, e);
            return OptionalLong.empty();
        }
    }

    /**
     * Fetch an object's bytes. Empty rather than throwing: one unreadable photo must not
     * cost the business the whole notification.
     */
    public Optional<StoredObject> getObject(String key) {
        try {
            ResponseBytes<GetObjectResponse> res = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucketName).key(key).build());
            return Optional.of(new StoredObject(key, res.asByteArray(), res.response().contentType()));
        } catch (Exception e) {
            log.error("Failed to fetch s3 object key={}", key, e);
            return Optional.empty();
        }
    }
}
