package com.deathnode.server.repository;

import com.deathnode.server.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<ReportEntity, String> {
    @Query("SELECT MAX(r.globalSequenceNumber) FROM ReportEntity r")
    Long findMaxGlobalSequenceNumber();
}
