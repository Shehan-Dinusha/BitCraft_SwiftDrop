package com.swiftdrop.dispatch;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The district queues of the two-leg flow as real SQL on Flyway-migrated tables (V12): which orders a
 * district handles, what waits at its hub to be sent, and what is coming in.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DispatchAssignmentService.class)
@Testcontainers(disabledWithoutDocker = true)
class HubQueuesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private DispatchAssignmentService service;
    @Autowired private DispatchAssignmentRepository repository;

    @BeforeEach
    void startFromEmptyTables() {
        repository.deleteAll();
    }

    private String order(String tracking, SriLankanDistrict from, SriLankanDistrict to, AssignmentStatus status,
                         AssignmentLeg leg, InternalQueueStatus hubStage) {
        DispatchAssignment a = new DispatchAssignment();
        a.setAssignmentId(UUID.randomUUID());
        a.setOrderId(UUID.randomUUID());
        a.setTrackingNumber(tracking);
        a.setPickupDistrict(from);
        a.setDeliveryDistrict(to);
        a.setOrderSpeed(OrderSpeed.STANDARD);
        a.setStatus(status);
        a.setLeg(leg);
        a.setHubStage(hubStage);
        a.setRetryCount(0);
        repository.save(a);
        return tracking;
    }

    private static List<String> tracking(List<DispatchAssignmentResponse> list) {
        return list.stream().map(DispatchAssignmentResponse::trackingNumber).toList();
    }

    @Test
    @DisplayName("Each district's queue holds its own first-leg orders and the last legs accepted at its hub")
    void districtQueues() {
        var C = SriLankanDistrict.COLOMBO;
        var K = SriLankanDistrict.KANDY;
        String colomboLocal = order("SD-COLO-LOCL", C, C, AssignmentStatus.UNASSIGNED, AssignmentLeg.FIRST_MILE, null);
        String colomboToKandyNew = order("SD-COLO-KAND", C, K, AssignmentStatus.UNASSIGNED, AssignmentLeg.FIRST_MILE, null);
        String atColomboHub = order("SD-HUBC-0001", C, K, AssignmentStatus.IN_TRANSFER, AssignmentLeg.FIRST_MILE,
                InternalQueueStatus.AT_HUB);
        String onTheWay = order("SD-ROAD-0001", C, K, AssignmentStatus.IN_TRANSFER, AssignmentLeg.FIRST_MILE,
                InternalQueueStatus.OUTBOUND);
        String atKandyHub = order("SD-HUBK-0001", C, K, AssignmentStatus.UNASSIGNED, AssignmentLeg.LAST_MILE,
                InternalQueueStatus.INCOMING);

        assertThat(tracking(service.getUnassignedParcels(C))).containsExactlyInAnyOrder(colomboLocal, colomboToKandyNew);
        assertThat(tracking(service.getUnassignedParcels(K))).containsExactly(atKandyHub);

        assertThat(tracking(service.getReadyToSend(C))).containsExactly(atColomboHub);
        assertThat(service.getReadyToSend(K)).isEmpty();

        // Coming into Kandy until its hub accepts it; then it is Kandy's own order.
        assertThat(tracking(service.getIncoming(K))).containsExactlyInAnyOrder(colomboToKandyNew, atColomboHub, onTheWay);

        assertThat(tracking(service.getAssignments(null, K, PageRequest.of(0, 20)).getContent()))
                .containsExactly(atKandyHub);
        assertThat(service.getAssignments(null, C, PageRequest.of(0, 20)).getContent()).hasSize(4);
    }
}
