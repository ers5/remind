package teample.remind.DTO;

public record UserRequestDTO(
        String appName,
        String userInput,
        String lockReason,
        CurrentStats currentStats
) {
    public record CurrentStats(
      Integer willPowerScore,
      Integer todayOpenAppCount,
      Integer accumUseApp
    ){}
}
