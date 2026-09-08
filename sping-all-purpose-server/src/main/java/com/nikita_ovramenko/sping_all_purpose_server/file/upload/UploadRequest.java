package com.nikita_ovramenko.sping_all_purpose_server.file.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The only thing a client gets to say about an upload.
 *
 * <p>Just the original file name, and only so the stored object keeps a recognisable
 * name and extension. It is sanitised before use; the rest of the key is built server
 * side.
 */
public record UploadRequest(@NotBlank @Size(max = 200) String fileName) {
}
