package com.spring.backend.repository;

import com.spring.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  @Query("SELECT COALESCE(SUM(d.size), 0L) FROM Document d WHERE d.owner.id = :ownerId")
  Long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);
}
