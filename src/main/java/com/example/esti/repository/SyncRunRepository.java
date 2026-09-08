package com.example.esti.repository;

import com.example.esti.entity.SyncRun;
import com.example.esti.entity.SyncRunType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    Optional<SyncRun> findByRunTypeAndRunKey(SyncRunType runType, String runKey);
}
