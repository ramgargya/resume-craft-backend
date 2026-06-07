package com.resumebuilder.backend.repository;

import com.resumebuilder.backend.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
