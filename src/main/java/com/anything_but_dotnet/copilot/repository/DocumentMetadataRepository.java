package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {
}