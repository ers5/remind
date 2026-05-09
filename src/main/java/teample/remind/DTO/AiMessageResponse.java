package teample.remind.DTO;

public record AiMessageResponse(
        String text,
        Integer allowedTime
) {
}
