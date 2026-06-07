package com.resumebuilder.backend.service;

import com.resumebuilder.backend.model.*;
import com.resumebuilder.backend.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;

    @Autowired
    public ResumeService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    public List<Resume> getAllResumes() {
        return resumeRepository.findAll();
    }

    public List<Resume> getResumesByUserId(Long userId) {
        return resumeRepository.findByUserId(userId);
    }

    public Optional<Resume> getResumeById(Long id) {
        return resumeRepository.findById(id);
    }

    @Transactional
    public Resume saveResume(Resume resume) {
        // Correctly wire back-references for JPA child entities before saving
        if (resume.getExperienceDetails() != null) {
            resume.getExperienceDetails().forEach(detail -> detail.setResume(resume));
        }
        if (resume.getEducationDetails() != null) {
            resume.getEducationDetails().forEach(detail -> detail.setResume(resume));
        }
        if (resume.getProjectDetails() != null) {
            resume.getProjectDetails().forEach(detail -> detail.setResume(resume));
        }
        if (resume.getSkills() != null) {
            resume.getSkills().forEach(detail -> detail.setResume(resume));
        }
        
        return resumeRepository.save(resume);
    }

    @Transactional
    public void deleteResume(Long id) {
        resumeRepository.deleteById(id);
    }
}
