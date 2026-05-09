package teample.remind.DTO;

public record UserResponseDTO(
        STATUS status,
        String text,
        Integer allowedTime
) {
}
