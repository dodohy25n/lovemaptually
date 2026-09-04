package com.lovemaptually.report.repository;

import com.lovemaptually.report.entity.MonthlyReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByGroupIdAndReportMonth(Long groupId, LocalDate reportMonth);

    List<MonthlyReport> findByGroupIdOrderByReportMonthDesc(Long groupId);
}
