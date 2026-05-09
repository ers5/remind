package teample.remind.DTO;

public record AiStatusJudgeResponse(
        String appName,
        STATUS status,
        String reason,
        Integer allowedTime
) {
}
