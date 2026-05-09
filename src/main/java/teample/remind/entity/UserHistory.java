package teample.remind.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import teample.remind.DTO.STATUS;

@Entity
@Table(name = "user_history",uniqueConstraints = {
        @UniqueConstraint(columnNames = {"app_name","user_input","will_power_level","usage_level","request_minutes"}
        )})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="app_name",nullable = false)
    private String appName;

    @Column(name = "user_input",nullable = false,columnDefinition = "TEXT")
    private String userInput;

    @Column(name = "will_power_level",nullable = false,length = 5)
    private String willPowerLevel;

    @Column(name="usage_level",nullable = false,length = 5)
    private String usageLevel;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    private STATUS status;

    @Column(name="use_count")
    private Long useCount=1L;

    @Column(name="request_minutes")
    private Integer requestMinutes;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name="embedding",nullable = false,columnDefinition = "vector(1024)")
    @Convert(converter = FloatArrayToVectorConverter.class)
    @ColumnTransformer(write = "?::vector")
    private float[] embedding;

    @Column(name = "allowed_time")
    private Integer allowedTime;

    public void incrementCount(){this.useCount++;}

    public UserHistory(String appName,String userInput, String willpowerLevel, String usageLevel, STATUS status, String reason, float[] embedding,Integer requestMinutes,Integer allowedTime) {
        this.appName = appName;
        this.userInput = userInput;
        this.willPowerLevel = willpowerLevel;
        this.usageLevel = usageLevel;
        this.status = status;
        this.reason = reason;
        this.embedding = embedding;
        this.requestMinutes = requestMinutes;
        this.allowedTime = allowedTime;
    }

}
