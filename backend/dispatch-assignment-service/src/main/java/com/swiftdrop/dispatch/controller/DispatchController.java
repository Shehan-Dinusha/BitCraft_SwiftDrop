package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.security.DistrictScope;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.CourierSuggestionResponse;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.security.DispatchAccess;
import com.swiftdrop.dispatch.service.CourierConflictRetry;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import com.swiftdrop.dispatch.service.ManualOverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch")
@Tag(name = "Dispatch", description = "Dispatch and courier assignment operations")
public class DispatchController {

    private static final Logger log = LoggerFactory.getLogger(DispatchController.class);

    private final ManualOverrideService manualOverrideService;
    private final DispatchAssignmentService dispatchAssignmentService;
    private final CourierConflictRetry courierConflictRetry;

    public DispatchController(
            ManualOverrideService manualOverrideService,
            DispatchAssignmentService dispatchAssignmentService,
            CourierConflictRetry courierConflictRetry) {
        this.manualOverrideService = manualOverrideService;
        this.dispatchAssignmentService = dispatchAssignmentService;
        this.courierConflictRetry = courierConflictRetry;
    }

    @GetMapping("/health")
    @Operation(summary = "Check service health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP", "DispatchController is ready");
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign a courier to an unassigned order",
            description = "Dispatcher confirms a courier for an order in the unassigned queue. "
                    + "Returns 409 if the order already has a courier; use /reassign to change it. "
                    + "A DISPATCHER may assign only orders picked up in their district (403 otherwise), and the "
                    + "courier must work in the order's pickup district. Requires DISPATCHER or SYSTEM_ADMIN.")
    public ApiResponse<DispatchAssignmentResponse> manualAssign(
            @Valid @RequestBody ManualAssignRequest request,
            @Parameter(description = "Request correlation ID, propagated to the published events when it is a UUID")
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationId,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        UUID eventCorrelationId = correlationIdOrNull(correlationId);
        var assignment = courierConflictRetry.run("Assignment",
                () -> manualOverrideService.manualAssign(request, eventCorrelationId, caller));
        return ApiResponse.success(
                DispatchAssignmentResponse.fromEntity(assignment),
                "Order successfully assigned to courier"
        );
    }

    @PostMapping("/reassign")
    @Operation(summary = "Reassign an order to a different courier",
            description = "Explicitly moves an assigned order to another courier. The request must name the courier "
                    + "currently assigned; returns 409 if the assignment changed since it was viewed. "
                    + "The same district rules as /assign apply. Requires DISPATCHER or SYSTEM_ADMIN.")
    public ApiResponse<DispatchAssignmentResponse> reassign(
            @Valid @RequestBody ReassignCourierRequest request,
            @Parameter(description = "Request correlation ID, propagated to the published events when it is a UUID")
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationId,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        UUID eventCorrelationId = correlationIdOrNull(correlationId);
        var assignment = courierConflictRetry.run("Reassignment",
                () -> manualOverrideService.reassign(request, eventCorrelationId, caller));
        return ApiResponse.success(
                DispatchAssignmentResponse.fromEntity(assignment),
                "Order successfully reassigned to courier"
        );
    }

    @GetMapping("/assignments")
    @Operation(summary = "List dispatch assignments with pagination and optional status filter",
            description = "A DISPATCHER gets the orders picked up in their own district (the district parameter is "
                    + "ignored); a SYSTEM_ADMIN gets every district or the one given.")
    public ApiResponse<PagedResult<DispatchAssignmentResponse>> getAssignments(
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) SriLankanDistrict district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest
    ) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        Page<DispatchAssignmentResponse> paged = dispatchAssignmentService.getAssignments(
                status,
                DistrictScope.listDistrict(caller, district),
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
    @Operation(summary = "Get dispatch assignment by UUID",
            description = "A COURIER sees only an assignment they currently hold (403 otherwise, "
                    + "including UNASSIGNED orders); DISPATCHER and SYSTEM_ADMIN see any.")
    public ApiResponse<DispatchAssignmentResponse> getAssignmentById(
            @PathVariable UUID assignmentId,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireCourierResourceRole(httpRequest);
        DispatchAssignmentResponse assignment = dispatchAssignmentService.getAssignmentById(assignmentId);
        requireLookupAccess(caller, assignment);
        return ApiResponse.success(assignment, "Dispatch assignment retrieved");
    }

    @GetMapping("/assignments/order/{orderId}")
    @Operation(summary = "Get dispatch assignment by order UUID",
            description = "A COURIER sees only an assignment they currently hold (403 otherwise, "
                    + "including UNASSIGNED orders); DISPATCHER and SYSTEM_ADMIN see any.")
    public ApiResponse<DispatchAssignmentResponse> getAssignmentByOrderId(
            @PathVariable UUID orderId,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireCourierResourceRole(httpRequest);
        DispatchAssignmentResponse assignment = dispatchAssignmentService.getAssignmentByOrderId(orderId);
        requireLookupAccess(caller, assignment);
        return ApiResponse.success(assignment, "Dispatch assignment retrieved");
    }

    @GetMapping("/unassigned")
    @Operation(summary = "List unassigned orders pending dispatch",
            description = "A DISPATCHER gets their own district's queue (the district parameter is ignored); a "
                    + "SYSTEM_ADMIN gets every district or the one given.")
    public ApiResponse<List<DispatchAssignmentResponse>> getUnassignedParcels(
            @RequestParam(required = false) SriLankanDistrict district,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        return ApiResponse.success(
                dispatchAssignmentService.getUnassignedParcels(DistrictScope.listDistrict(caller, district)),
                "Unassigned orders retrieved"
        );
    }

    @GetMapping("/incoming")
    @Operation(summary = "List open orders coming into a district from another one",
            description = "Read-only: the pickup district's dispatcher manages them. A DISPATCHER gets their own "
                    + "district; a SYSTEM_ADMIN must give the district.")
    public ApiResponse<List<DispatchAssignmentResponse>> getIncoming(
            @RequestParam(required = false) SriLankanDistrict district,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        SriLankanDistrict scope = DistrictScope.listDistrict(caller, district);
        if (scope == null) {
            throw new IllegalArgumentException("Choose a district");
        }
        return ApiResponse.success(dispatchAssignmentService.getIncoming(scope), "Incoming orders retrieved");
    }

    @GetMapping("/couriers/{courierId}/assignments")
    @Operation(summary = "List active assignments for a courier",
            description = "A COURIER may use only their own courier ID; DISPATCHER and SYSTEM_ADMIN may use any.")
    public ApiResponse<List<DispatchAssignmentResponse>> getCourierAssignments(
            @PathVariable UUID courierId,
            @RequestParam(required = false) AssignmentStatus status,
            HttpServletRequest httpRequest
    ) {
        DispatchAccess.requireCourierSelfOrOperations(httpRequest, courierId);
        return ApiResponse.success(
                dispatchAssignmentService.getAssignmentsForCourier(courierId, status),
                "Courier assignments retrieved"
        );
    }

    @GetMapping("/orders/{orderId}/suggestions")
    @Operation(summary = "Suggest available couriers for an order",
            description = "Returns up to three available couriers working in the order's pickup district, lowest "
                    + "workload first. For an assigned order the current courier is left out (reassignment "
                    + "suggestions); a completed or cancelled order returns 409. A DISPATCHER only for orders picked "
                    + "up in their district.")
    public ApiResponse<List<CourierSuggestionResponse>> getCourierSuggestions(
            @PathVariable UUID orderId,
            HttpServletRequest httpRequest) {
        CallerContext caller = DispatchAccess.requireOperations(httpRequest);
        DistrictScope.requireManage(caller, dispatchAssignmentService.getAssignmentByOrderId(orderId).pickupDistrict());
        return ApiResponse.success(
                dispatchAssignmentService.getSuggestions(orderId),
                "Courier suggestions retrieved"
        );
    }

    /** A courier sees only what they hold; a dispatcher only orders picked up in or bound for their district. */
    private static void requireLookupAccess(CallerContext caller, DispatchAssignmentResponse assignment) {
        DispatchAccess.requireAssignmentAccess(caller, assignment.courierId());
        if (DispatchAccess.isOperations(caller)) {
            DistrictScope.requireView(caller, assignment.pickupDistrict(), assignment.deliveryDistrict());
        }
    }

    /**
     * Event contracts carry the correlation ID as a UUID. The gateway forwards any
     * non-blank client value, so a value that is not a UUID is not propagated and
     * the service generates a new one instead of failing the request.
     */
    static UUID correlationIdOrNull(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring {} header that is not a UUID", JwtHeaderConstants.X_CORRELATION_ID);
            return null;
        }
    }
}
