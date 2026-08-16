package com.docfitai.backend.insurance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkSourceRepository extends JpaRepository<NetworkSource, Long> {

    List<NetworkSource> findByPayerId(Long payerId);
}
