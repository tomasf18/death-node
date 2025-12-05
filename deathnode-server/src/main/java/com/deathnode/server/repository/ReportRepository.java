package com.deathnode.server.repository;

import com.deathnode.server.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, String> {

    List<Report> findAllBySignerNodeIdOrderBySequenceNumberAsc(String signerNodeId);

}
