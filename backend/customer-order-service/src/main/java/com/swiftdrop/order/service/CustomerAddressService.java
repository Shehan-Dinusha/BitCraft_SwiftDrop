package com.swiftdrop.order.service;

import com.swiftdrop.order.domain.entity.CustomerAddress;
import com.swiftdrop.order.domain.repository.CustomerAddressRepository;
import com.swiftdrop.order.dto.SavedAddressRequest;
import com.swiftdrop.order.dto.SavedAddressResponse;
import com.swiftdrop.order.exception.AddressBookException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A customer's address book. Every operation is scoped to the caller: another customer's address is
 * reported as not found, never as forbidden, so IDs cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    /** Enough for home, work, family and regular recipients without becoming a contact list. */
    public static final int MAX_ADDRESSES = 20;

    private final CustomerAddressRepository addresses;

    @Transactional(readOnly = true)
    public List<SavedAddressResponse> list(UUID customerId) {
        return addresses.findByCustomerIdOrderByUpdatedAtDesc(customerId).stream()
                .map(SavedAddressResponse::from)
                .toList();
    }

    @Transactional
    public SavedAddressResponse create(UUID customerId, SavedAddressRequest request) {
        if (addresses.countByCustomerId(customerId) >= MAX_ADDRESSES) {
            throw AddressBookException.full(MAX_ADDRESSES);
        }
        CustomerAddress address = CustomerAddress.builder().customerId(customerId).build();
        apply(address, request);
        return SavedAddressResponse.from(addresses.save(address));
    }

    @Transactional
    public SavedAddressResponse update(UUID customerId, UUID addressId, SavedAddressRequest request) {
        CustomerAddress address = owned(customerId, addressId);
        apply(address, request);
        return SavedAddressResponse.from(addresses.save(address));
    }

    @Transactional
    public void delete(UUID customerId, UUID addressId) {
        addresses.delete(owned(customerId, addressId));
    }

    private CustomerAddress owned(UUID customerId, UUID addressId) {
        return addresses.findByAddressIdAndCustomerId(addressId, customerId)
                .orElseThrow(AddressBookException::notFound);
    }

    private static void apply(CustomerAddress address, SavedAddressRequest r) {
        address.setLabel(r.label().trim());
        address.setContactName(r.contactName().trim());
        address.setContactPhone(phone(r.contactPhone()));
        address.setAlternatePhone(blankToNull(r.alternatePhone()) == null ? null : phone(r.alternatePhone()));
        address.setHouse(r.house().trim());
        address.setStreet(r.street().trim());
        address.setArea(blankToNull(r.area()));
        address.setLandmark(blankToNull(r.landmark()));
        address.setCity(r.city().trim());
        address.setDistrict(r.district());
        address.setPostalCode(r.postalCode().trim());
    }

    /** "0771234567" -> "+94771234567", the format orders store. The request pattern has checked the shape. */
    static String phone(String value) {
        String number = value.trim();
        return number.startsWith("0") ? "+94" + number.substring(1) : number;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
