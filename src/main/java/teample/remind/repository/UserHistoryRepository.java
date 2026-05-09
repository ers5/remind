package teample.remind.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import teample.remind.entity.UserHistory;

import java.util.*;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

    @Query(value = """
    SELECT *
    FROM user_history
    WHERE app_name = :appName
    AND will_power_level = :willPowerLevel
    AND usage_level = :usageLevel
    AND (
        (:requestMinutes IS NULL AND request_minutes IS NULL)
        OR
        (:requestMinutes IS NOT NULL AND request_minutes IS NOT NULL
            AND request_minutes BETWEEN (:requestMinutes) AND (:requestMinutes + 5)
                    )
    )
    ORDER BY embedding <=> cast(:embedding as vector)
    LIMIT 1
    """, nativeQuery = true)
    Optional<UserHistory> findCloserHistory(
            @Param("embedding") float[] embedding,
            @Param("appName") String appName,
            @Param("willPowerLevel") String willPowerLevel,
            @Param("usageLevel") String usageLevel,
            @Param("requestMinutes") Integer requestMinutes
    );
}
