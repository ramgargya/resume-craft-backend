package com.resumebuilder.backend.service;

import com.resumebuilder.backend.model.*;
import com.resumebuilder.backend.repository.ResumeRepository;
import org.hibernate.Hibernate;
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

    private void initializeResumeCollections(Resume resume) {
        if (resume == null) return;
        Hibernate.initialize(resume.getExperienceDetails());
        Hibernate.initialize(resume.getEducationDetails());
        Hibernate.initialize(resume.getProjectDetails());
        Hibernate.initialize(resume.getSkills());
        Hibernate.initialize(resume.getAchievements());
        Hibernate.initialize(resume.getExtracurriculars());
        Hibernate.initialize(resume.getHobbies());
    }

    @Transactional(readOnly = true)
    public List<Resume> getAllResumes() {
        List<Resume> resumes = resumeRepository.findAll();
        resumes.forEach(this::initializeResumeCollections);
        return resumes;
    }

    @Transactional(readOnly = true)
    public List<Resume> getResumesByUserId(Long userId) {
        List<Resume> resumes = resumeRepository.findByUserId(userId);
        resumes.forEach(this::initializeResumeCollections);
        return resumes;
    }

    @Transactional(readOnly = true)
    public Optional<Resume> getResumeById(Long id) {
        Optional<Resume> resumeOpt = resumeRepository.findById(id);
        resumeOpt.ifPresent(this::initializeResumeCollections);
        return resumeOpt;
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
        
        Resume savedResume = resumeRepository.save(resume);
        initializeResumeCollections(savedResume);
        return savedResume;
    }

    @Transactional
    public void deleteResume(Long id) {
        resumeRepository.deleteById(id);
    }
}
