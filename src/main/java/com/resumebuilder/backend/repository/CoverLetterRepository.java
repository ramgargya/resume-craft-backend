package com.resumebuilder.backend.repository;

import com.resumebuilder.backend.model.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    List<CoverLetter> findByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
