package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.delivery.dto.CashDepositResponse;
import com.swiftdrop.delivery.dto.CashDeskResponse;
import com.swiftdrop.delivery.dto.CashEntryResponse;
import com.swiftdrop.delivery.dto.CashHandoverResponse;
import com.swiftdrop.delivery.dto.CashReportResponse;
import com.swiftdrop.delivery.dto.CountHandoverRequest;
import com.swiftdrop.delivery.dto.CourierCashResponse;
import com.swiftdrop.delivery.dto.CreateDepositRequest;
import com.swiftdrop.delivery.dto.RefundFeeRequest;
import com.swiftdrop.delivery.dto.RejectDepositRequest;
import com.swiftdrop.delivery.service.CashDeskService;
import com.swiftdrop.delivery.service.CashReportService;
import com.swiftdrop.delivery.service.CourierCashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash")
@Tag(name = "Cash", description = "Cash couriers collect, hand over to their hub, and the hub banks")
public class CashController {

    private final CourierCashService courierCashService;
    private final CashDeskService cashDeskService;
    private final CashReportService cashReportService;

    public CashController(CourierCashService courierCashService, CashDeskService cashDeskService,
                          CashReportService cashReportService) {
        this.courierCashService = courierCashService;
        this.cashDeskService = cashDeskService;
        this.cashReportService = cashReportService;
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in courier's cash and latest handovers")
    public ApiResponse<CourierCashResponse> mine(HttpServletRequest http) {
        return ApiResponse.success(courierCashService.mine(caller(http)), "Cash retrieved");
    }

    @PostMapping("/handovers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Courier hands over all the cash they hold",
            description = "To the dispatcher of their district. Required before the courier ends their shift.")
    public ApiResponse<CashHandoverResponse> submit(HttpServletRequest http) {
        return ApiResponse.success(courierCashService.submit(caller(http)), "Cash submitted");
    }

    @GetMapping("/desk")
    @Operation(summary = "A hub's cash",
            description = "A DISPATCHER gets their own district (the parameter is ignored); a SYSTEM_ADMIN gets every "
                    + "district or the one given.")
    public ApiResponse<CashDeskResponse> desk(@RequestParam(required = false) SriLankanDistrict district,
                                              HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.desk(caller(http), district), "Cash desk retrieved");
    }

    @GetMapping("/report")
    @Operation(summary = "Revenue and cash for 1 to 90 inclusive Colombo dates",
            description = "A DISPATCHER gets their own district (the parameter is ignored); a SYSTEM_ADMIN gets every "
                    + "district or the one given.")
    public ApiResponse<CashReportResponse> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SriLankanDistrict district,
            HttpServletRequest http) {
        return ApiResponse.success(cashReportService.report(caller(http), from, to, district), "Cash report retrieved");
    }

    @GetMapping("/handovers/{handoverId}/entries")
    @Operation(summary = "The parcels a handover came from",
            description = "For the courier who handed it over, their district's DISPATCHER, or a SYSTEM_ADMIN.")
    public ApiResponse<List<CashEntryResponse>> handoverEntries(@PathVariable UUID handoverId, HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.handoverEntries(caller(http), handoverId), "Handover entries retrieved");
    }

    @PostMapping("/handovers/{handoverId}/count")
    @Operation(summary = "Dispatcher counts a handover",
            description = "Only the DISPATCHER of that district. A different amount needs a note.")
    public ApiResponse<CashHandoverResponse> count(@PathVariable UUID handoverId,
                                                   @Valid @RequestBody CountHandoverRequest request,
                                                   HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.count(caller(http), handoverId, request), "Handover counted");
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dispatcher banks all the cash at their hub")
    public ApiResponse<CashDepositResponse> deposit(@Valid @RequestBody CreateDepositRequest request, HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.deposit(caller(http), request), "Deposit recorded");
    }

    @PostMapping("/deposits/{depositId}/confirm")
    @Operation(summary = "SYSTEM_ADMIN confirms a deposit reached the bank")
    public ApiResponse<CashDepositResponse> confirm(@PathVariable UUID depositId, HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.confirm(caller(http), depositId), "Deposit confirmed");
    }

    @PostMapping("/deposits/{depositId}/reject")
    @Operation(summary = "SYSTEM_ADMIN rejects a deposit", description = "Its cash goes back to the hub.")
    public ApiResponse<CashDepositResponse> reject(@PathVariable UUID depositId,
                                                   @Valid @RequestBody RejectDepositRequest request,
                                                   HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.reject(caller(http), depositId, request), "Deposit rejected");
    }

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dispatcher refunds a delivery fee from the hub's cash",
            description = "Only for a delivery that failed because SwiftDrop lost or damaged the parcel, and only by the "
                    + "DISPATCHER of the district that collected the fee.")
    public ApiResponse<CashEntryResponse> refund(@Valid @RequestBody RefundFeeRequest request, HttpServletRequest http) {
        return ApiResponse.success(cashDeskService.refund(caller(http), request), "Fee refunded");
    }

    private static CallerContext caller(HttpServletRequest http) {
        return CallerContext.from(http::getHeader);
    }
}
