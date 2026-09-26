package com.spring.backend.repository;

import com.spring.backend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository 
public interface FolderRepository extends JpaRepository<Folder, UUID> {

}