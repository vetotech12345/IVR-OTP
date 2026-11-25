package com.otpservice.otp;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository
        extends JpaRepository<OtpChallenge, UUID>, JpaSpecificationExecutor<OtpChallenge> {

    /**
     * Locked, so two verifies of one challenge at the same instant are serialised: otherwise both could read
     * {@code attempts = 4} of 5 and both get a guess. Scoped by app, so one app cannot verify another's code.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c where c.id = :id and c.appId = :appId")
    Optional<OtpChallenge> lockForVerify(@Param("id") UUID id, @Param("appId") UUID appId);

    @Query("select count(c) from OtpChallenge c where c.appId = :appId and c.phone = :phone and c.createdAt > :since")
    long countSends(@Param("appId") UUID appId, @Param("phone") String phone, @Param("since") Instant since);

    @Query("select max(c.createdAt) from OtpChallenge c where c.appId = :appId and c.phone = :phone")
    Optional<Instant> lastSendAt(@Param("appId") UUID appId, @Param("phone") String phone);

    @Query("select count(c) from OtpChallenge c where c.requestIp = :ip and c.createdAt > :since")
    long countSendsFromIp(@Param("ip") String ip, @Param("since") Instant since);

    // ------------------------------------------------------------- console

    /** Per app since {@code since}: [appId, sends, verified, lastSendAt]. */
    @Query("""
            select c.appId, count(c), sum(case when c.verifiedAt is not null then 1 else 0 end), max(c.createdAt)
            from OtpChallenge c where c.createdAt > :since group by c.appId""")
    List<Object[]> usageByApp(@Param("since") Instant since);

    /** Per channel since {@code since}: [channel, sends]. */
    @Query("select c.channel, count(c) from OtpChallenge c where c.createdAt > :since group by c.channel")
    List<Object[]> usageByChannel(@Param("since") Instant since);

    @Query("select count(c) from OtpChallenge c where c.createdAt > :since")
    long countSince(@Param("since") Instant since);

    @Query("select count(c) from OtpChallenge c where c.createdAt > :since and c.verifiedAt is not null")
    long countVerifiedSince(@Param("since") Instant since);

    /** All time, per app: [appId, lastSendAt]. */
    @Query("select c.appId, max(c.createdAt) from OtpChallenge c group by c.appId")
    List<Object[]> lastSendByApp();
}
