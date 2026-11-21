package pl.kas.olcys.api.dto;

public record AddressScoreChangeDto(
        String address,
        String network,
        Integer oldScore,
        Integer newScore,
        String changeType
) {
}
