package com.otpservice.app;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientAppRepository extends JpaRepository<ClientApp, UUID> {

    @Query("select count(a) > 0 from ClientApp a where lower(a.name) = lower(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    List<ClientApp> findAllByOrderByNameAsc();
}
