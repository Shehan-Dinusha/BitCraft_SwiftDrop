package com.swiftdrop.order.controller;

import com.swiftdrop.order.dto.SavedAddressResponse;
import com.swiftdrop.order.exception.AddressBookException;
import com.swiftdrop.order.service.CustomerAddressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerAddressController.class)
class CustomerAddressControllerTest {

    private static final String URL = "/api/v1/orders/addresses";
    private static final String VALID = """
            {"label":"Home","contactName":"Nimal Perera","contactPhone":"+94771234567",
             "house":"45/2","street":"Temple Road","area":"","landmark":"Near the post office",
             "city":"Nugegoda","district":"COLOMBO","postalCode":"10250"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean CustomerAddressService addresses;

    private final String customer = UUID.randomUUID().toString();

    @Test
    void aCustomerListsAndSavesOwnAddresses() throws Exception {
        when(addresses.list(UUID.fromString(customer))).thenReturn(List.of());
        mvc.perform(get(URL).header("X-User-Id", customer).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk());

        when(addresses.create(eq(UUID.fromString(customer)), any())).thenReturn(response());
        mvc.perform(post(URL).header("X-User-Id", customer).header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.label").value("Home"));
    }

    @Test
    void onlyASignedInCustomerHasAnAddressBook() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("X-User-Id", customer).header("X-User-Role", "MERCHANT"))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL).header("X-User-Id", customer).header("X-User-Role", "DISPATCHER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(addresses);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"contactPhone\":\"+94771234567\"|\"contactPhone\":\"12345\"",
            "\"postalCode\":\"10250\"|\"postalCode\":\"1025\"",
            "\"house\":\"45/2\"|\"house\":\"---\"",
            "\"street\":\"Temple Road\"|\"street\":\"<b>Road</b>\"",
            "\"contactName\":\"Nimal Perera\"|\"contactName\":\"N1mal\"",
            "\"label\":\"Home\"|\"label\":\"\"",
            "\"district\":\"COLOMBO\"|\"district\":\"ATLANTIS\""
    })
    void anImpracticalAddressIsRejected(String from, String to) throws Exception {
        mvc.perform(post(URL).header("X-User-Id", customer).header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace(from, to)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(addresses);
    }

    @Test
    void aFullBookAndAnUnknownAddressGetClearAnswers() throws Exception {
        when(addresses.create(any(), any())).thenThrow(AddressBookException.full(20));
        mvc.perform(post(URL).header("X-User-Id", customer).header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can save up to 20 addresses. Delete one to add another."));

        UUID other = UUID.randomUUID();
        doThrow(AddressBookException.notFound()).when(addresses).delete(UUID.fromString(customer), other);
        mvc.perform(delete(URL + "/" + other).header("X-User-Id", customer).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    private static SavedAddressResponse response() {
        return new SavedAddressResponse(UUID.randomUUID(), "Home", "Nimal Perera", "+94771234567", null, "45/2",
                "Temple Road", null, "Near the post office", "Nugegoda",
                com.swiftdrop.contracts.enums.SriLankanDistrict.COLOMBO, "10250", java.time.Instant.now());
    }
}
