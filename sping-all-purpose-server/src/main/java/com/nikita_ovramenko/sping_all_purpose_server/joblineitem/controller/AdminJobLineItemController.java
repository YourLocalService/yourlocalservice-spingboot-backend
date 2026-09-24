package com.nikita_ovramenko.sping_all_purpose_server.joblineitem.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.dto.JobLineItemCreateRequest;
import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.dto.JobLineItemResponse;
import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.dto.JobLineItemUpdateRequest;
import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.service.JobLineItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The lines of a job: what is actually being done, at what price, and how far along.
 */
@RestController
@RequestMapping("/api/admin/jobs/{jobId}/items")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: job line items", description = "Add, price and progress the lines of a job")
public class AdminJobLineItemController {

    private final JobLineItemService lineItemService;

    public AdminJobLineItemController(JobLineItemService lineItemService) {
        this.lineItemService = lineItemService;
    }

    @GetMapping
    @Operation(summary = "List the lines on a job")
    public List<JobLineItemResponse> list(@PathVariable Long jobId) {
        return lineItemService.list(jobId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a service to the job",
            description = "422 if the organization does not offer that service. The same "
                    + "service may appear more than once on a job.")
    public JobLineItemResponse add(@PathVariable Long jobId,
            @Valid @RequestBody JobLineItemCreateRequest request) {
        return lineItemService.add(jobId, request);
    }

    @PatchMapping("/{itemId}")
    @Operation(summary = "Update the price, quantity, description or status of a line",
            description = "Omitted or null fields are left unchanged.")
    public JobLineItemResponse update(@PathVariable Long jobId, @PathVariable Long itemId,
            @Valid @RequestBody JobLineItemUpdateRequest request) {
        return lineItemService.update(jobId, itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a line from the job")
    public void delete(@PathVariable Long jobId, @PathVariable Long itemId) {
        lineItemService.delete(jobId, itemId);
    }
    //
}
