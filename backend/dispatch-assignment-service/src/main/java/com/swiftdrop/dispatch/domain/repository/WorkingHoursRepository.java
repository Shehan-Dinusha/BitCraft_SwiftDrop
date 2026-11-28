package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.dispatch.domain.entity.WorkingHoursSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHoursRepository extends JpaRepository<WorkingHoursSetting, Short> {
}
