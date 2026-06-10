package com.resumebuilder.backend.service;

import com.resumebuilder.backend.model.*;
import com.resumebuilder.backend.repository.ChatMessageRepository;
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
    private final ChatMessageRepository chatMessageRepository;

    @Autowired
    public ResumeService(ResumeRepository resumeRepository, ChatMessageRepository chatMessageRepository) {
        this.resumeRepository = resumeRepository;
        this.chatMessageRepository = chatMessageRepository;
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
        boolean isNew = (resume.getId() == null);
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

        // Migrate unsaved chat thread if this is a newly saved resume
        if (isNew && resume.getUserId() != null) {
            String newTitle = (savedResume.getName() != null && !savedResume.getName().trim().isEmpty()) 
                ? savedResume.getName().trim() + " - resume" 
                : "untitled - resume";
            chatMessageRepository.migrateThreadId(resume.getUserId(), "resume-unsaved", "resume-" + savedResume.getId(), newTitle);
        }

        initializeResumeCollections(savedResume);
        return savedResume;
    }

    @Transactional
    public void deleteResume(Long id) {
        resumeRepository.deleteById(id);
    }
}
