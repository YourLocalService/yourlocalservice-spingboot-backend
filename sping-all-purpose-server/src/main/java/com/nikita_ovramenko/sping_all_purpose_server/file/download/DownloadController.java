package com.nikita_ovramenko.sping_all_purpose_server.file.download;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nikita_ovramenko.sping_all_purpose_server.file.FileService;
import com.nikita_ovramenko.sping_all_purpose_server.organization.service.OrganizationLookup;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;


@RestController 
@RequestMapping("/api/downloads")
public class DownloadController {

    private final FileService fileService;
    private final OrganizationLookup organizationLookup;

    public DownloadController(FileService fileService, OrganizationLookup organizationLookup){
        this.fileService = fileService;
        this.organizationLookup = organizationLookup;
    }

    @PostMapping("/s3/images")
    public DownloadTarget getImageLinks(@Valid @RequestBody DownloadRequest request) {
        List<String> links = fileService.createPresignedGetLinks(request.keys());
        return new DownloadTarget(links);
    }
    

}
