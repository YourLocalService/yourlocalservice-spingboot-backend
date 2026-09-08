package com.nikita_ovramenko.sping_all_purpose_server.file.upload;

/**
 * Where to PUT one photo, and the key to send back with the quote.
 *
 * <p>The client stores {@code key} and passes it in the quote's pictureKeys; it never
 * constructs one itself.
 */
public record UploadTarget(String key, String url) {
}
