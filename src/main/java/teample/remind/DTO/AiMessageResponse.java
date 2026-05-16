package teample.remind.DTO;

public record AiMessageResponse(
        STATUS status,
        String text,
        Integer allowedTime
) {
}
