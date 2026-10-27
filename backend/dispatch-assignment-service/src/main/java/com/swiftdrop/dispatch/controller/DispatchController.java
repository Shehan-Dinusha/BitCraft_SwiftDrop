package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch")
@Tag(name = "Dispatch", description = "Dispatch and courier assignment operations")
public class DispatchController {

    private final ManualOverrideService manualOverrideService;
    private final DispatchAssignmentService dispatchAssignmentService;

    public DispatchController(
            ManualOverrideService manualOverrideService,
            DispatchAssignmentService dispatchAssignmentService) {
        this.manualOverrideService = manualOverrideService;
        this.dispatchAssignmentService = dispatchAssignmentService;
    }

    @GetMapping("/health")
    @Operation(summary = "Check service health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP", "DispatchController is ready");
    }

    @PostMapping("/assign")
    @Operation(summary = "Manual courier assignment override", description = "Allows dispatcher to manually assign or reassign a courier to an order.")
    public ApiResponse<DispatchAssignmentResponse> manualAssign(@Valid @RequestBody ManualAssignRequest request) {
        var assignment = manualOverrideService.manualAssign(request);
        return ApiResponse.success(
                DispatchAssignmentResponse.fromEntity(assignment),
                "Order successfully assigned to courier"
        );
    }

    @GetMapping("/assignments")
    @Operation(summary = "List dispatch assignments with pagination and optional status filter")
    public ApiResponse<PagedResult<DispatchAssignmentResponse>> getAssignments(
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<DispatchAssignmentResponse> paged = dispatchAssignmentService.getAssignments(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        PagedResult<DispatchAssignmentResponse> result = PagedResult.of(
                paged.getContent(),
                paged.getNumber(),
                paged.getSize(),
                paged.getTotalElements(),
                paged.getTotalPages()
        );
        return ApiResponse.success(result, "Dispatch assignments retrieved");
    }

    @GetMapping("/assignments/{assignmentId}")
    @Operation(summary = "Get dispatch assignment by UUID")
    public ApiResponse<DispatchAssignmentResponse> getAssignmentById(@PathVariable UUID assignmentId) {
        return ApiResponse.success(
                dispatchAssignmentService.getAssignmentById(assignmentId),
                "Dispatch assignment retrieved"
        );
    }

    @GetMapping("/assignments/order/{orderId}")
    @Operation(summary = "Get dispatch assignment by order UUID")
    public ApiResponse<DispatchAssignmentResponse> getAssignmentByOrderId(@PathVariable UUID orderId) {
        return ApiResponse.success(
                dispatchAssignmentService.getAssignmentByOrderId(orderId),
                "Dispatch assignment retrieved"
        );
    }

    @GetMapping("/unassigned")
    @Operation(summary = "List unassigned orders pending dispatch")
    public ApiResponse<List<DispatchAssignmentResponse>> getUnassignedParcels() {
        return ApiResponse.success(
                dispatchAssignmentService.getUnassignedParcels(),
                "Unassigned orders retrieved"
        );
    }

    @GetMapping("/couriers/{courierId}/assignments")
    @Operation(summary = "List active assignments for a courier")
    public ApiResponse<List<DispatchAssignmentResponse>> getCourierAssignments(
            @PathVariable UUID courierId,
            @RequestParam(required = false) AssignmentStatus status
    ) {
        return ApiResponse.success(
                dispatchAssignmentService.getAssignmentsForCourier(courierId, status),
                "Courier assignments retrieved"
        );
    }
}
