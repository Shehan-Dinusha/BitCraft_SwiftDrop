package com.swiftdrop.order;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerAddress;
import com.swiftdrop.order.domain.repository.CustomerAddressRepository;
import com.swiftdrop.order.domain.repository.CustomerOrderRepository;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.dto.SavedAddressRequest;
import com.swiftdrop.order.exception.AddressBookException;
import com.swiftdrop.order.service.CustomerAddressService;
import com.swiftdrop.order.service.CustomerOrderBookingService;
import com.swiftdrop.order.service.pricing.PricingClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V7 on a real PostgreSQL: the address book keeps each customer's entries apart and caps them, and a
 * booking retried with the same clientRequestId returns the first order instead of creating a second.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AddressBookAndRetryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired CustomerAddressService addressService;
    @Autowired CustomerAddressRepository addressRepository;
    @Autowired CustomerOrderBookingService bookings;
    @Autowired CustomerOrderRepository orders;
    @MockitoBean PricingClient pricingClient;

    @Test
    void eachCustomerSeesOnlyTheirOwnAddressesUpToTheLimit() {
        UUID nimal = UUID.randomUUID();
        UUID kamala = UUID.randomUUID();
        var saved = addressService.create(nimal, address("Home", "0771234567"));
        addressService.create(kamala, address("Shop", "+94712345678"));

        assertThat(saved.contactPhone()).isEqualTo("+94771234567");
        assertThat(addressService.list(nimal)).extracting("label").containsExactly("Home");
        assertThatThrownBy(() -> addressService.delete(kamala, saved.addressId()))
                .isInstanceOf(AddressBookException.class);

        for (int i = 1; i < CustomerAddressService.MAX_ADDRESSES; i++) {
            addressService.create(nimal, address("Place " + i, "0771234567"));
        }
        assertThatThrownBy(() -> addressService.create(nimal, address("One too many", "0771234567")))
                .isInstanceOf(AddressBookException.class);
        assertThat(addressRepository.countByCustomerId(nimal)).isEqualTo(CustomerAddressService.MAX_ADDRESSES);

        CustomerAddress stored = addressRepository.findByAddressIdAndCustomerId(saved.addressId(), nimal).orElseThrow();
        assertThat(stored.getDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
    }

    @Test
    void aRetriedBookingReturnsTheFirstOrder() {
        when(pricingClient.estimate(any())).thenReturn(FeeEstimateResponse.builder()
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .speed(com.swiftdrop.contracts.enums.OrderSpeed.STANDARD)
                .chargeableWeightKg(new BigDecimal("1.00"))
                .estimatedFee(new BigDecimal("650.00"))
                .pricingRuleVersionId("PRICING-IT-V1")
                .currency("LKR")
                .build());
        UUID customer = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        var first = bookings.book(order(requestId), customer, null);
        var retry = bookings.book(order(requestId), customer, null);
        var another = bookings.book(order(UUID.randomUUID()), customer, null);

        assertThat(retry.getOrderId()).isEqualTo(first.getOrderId());
        assertThat(retry.getTrackingNumber()).isEqualTo(first.getTrackingNumber());
        assertThat(another.getOrderId()).isNotEqualTo(first.getOrderId());
        assertThat(orders.findAll()).filteredOn(o -> customer.equals(o.getCustomerId())).hasSize(2);
    }

    private static SavedAddressRequest address(String label, String phone) {
        return new SavedAddressRequest(label, "Nimal Perera", phone, null, "45/2", "Temple Road", null,
                "Near the post office", "Nugegoda", SriLankanDistrict.COLOMBO, "10250");
    }

    private static CreateOrderRequest order(UUID clientRequestId) {
        return CreateOrderRequest.builder()
                .clientRequestId(clientRequestId)
                .pickupAddressLine1("45/2, Temple Road").pickupCity("Nugegoda")
                .pickupDistrict(SriLankanDistrict.COLOMBO).pickupPostalCode("10250")
                .pickupContactName("Nimal Perera").pickupContactPhone("+94771234567")
                .deliveryAddressLine1("12, Peradeniya Road").deliveryCity("Kandy")
                .deliveryDistrict(SriLankanDistrict.KANDY).deliveryPostalCode("20000")
                .recipientName("Kamala Silva").recipientPhone("+94712345678")
                .actualWeightKg(new BigDecimal("1.0")).lengthCm(new BigDecimal("20"))
                .widthCm(new BigDecimal("15")).heightCm(new BigDecimal("10"))
                .speed(com.swiftdrop.contracts.enums.OrderSpeed.STANDARD)
                .prohibitedItemsDeclared(true)
                .build();
    }
}
