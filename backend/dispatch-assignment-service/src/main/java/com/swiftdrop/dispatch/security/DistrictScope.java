package com.swiftdrop.dispatch.security;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;

/**
 * District boundaries for dispatchers. An order belongs to its pickup district: that district's
 * dispatcher sees it, assigns and reassigns its courier, and only couriers working in that district
 * can carry it. The drop-off district's dispatcher sees it too, read-only, as incoming. The
 * dispatcher's district comes from the gateway (X-District-Id, from their account), never from the
 * request. A SYSTEM_ADMIN does not do dispatch work, so dispatch endpoints never reach here for one.
 * {@code DispatchAccess} rejects them first.
 */
public final class DistrictScope {

    private DistrictScope() {
    }

    /** The district a list is limited to: always the dispatcher's own. */
    public static SriLankanDistrict listDistrict(CallerContext caller) {
        return ownDistrict(caller);
    }

    /** May the caller see an order with these districts? Its pickup or its drop-off district. */
    public static void requireView(CallerContext caller, SriLankanDistrict pickup, SriLankanDistrict delivery) {
        SriLankanDistrict own = ownDistrict(caller);
        if (own != pickup && own != delivery) {
            throw new ForbiddenException("This order is not in your district");
        }
    }

    /** May the caller assign or reassign the courier of an order picked up in {@code pickup}? */
    public static void requireManage(CallerContext caller, SriLankanDistrict pickup) {
        if (ownDistrict(caller) != pickup) {
            throw new ForbiddenException("This order is handled by the " + label(pickup) + " dispatcher");
        }
    }

    /** May the caller act on a courier working in {@code courierDistrict}? */
    public static void requireCourier(CallerContext caller, SriLankanDistrict courierDistrict) {
        if (caller.role() != UserRole.DISPATCHER) {
            return;
        }
        if (ownDistrict(caller) != courierDistrict) {
            throw new ForbiddenException("This courier works in another district");
        }
    }

    private static SriLankanDistrict ownDistrict(CallerContext caller) {
        if (caller.districtId() == null) {
            // A dispatcher account always has a district (IAM enforces it); without one, no data.
            throw new ForbiddenException("Your account has no district");
        }
        return caller.districtId();
    }

    /** "NUWARA_ELIYA" -> "Nuwara Eliya". */
    public static String label(SriLankanDistrict district) {
        if (district == null) {
            return "another";
        }
        StringBuilder out = new StringBuilder();
        for (String part : district.name().split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return out.toString();
    }
}
