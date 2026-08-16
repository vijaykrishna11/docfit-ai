package com.docfitai.backend.provider.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataImportRepository extends JpaRepository<DataImport, Long> {
}
