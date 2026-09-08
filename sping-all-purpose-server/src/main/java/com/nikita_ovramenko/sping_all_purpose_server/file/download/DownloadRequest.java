package com.nikita_ovramenko.sping_all_purpose_server.file.download;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record DownloadRequest(
    @NotEmpty @Size(max = 200) List<@NotBlank String> keys
) {}
