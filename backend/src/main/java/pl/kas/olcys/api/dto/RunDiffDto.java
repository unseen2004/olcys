package pl.kas.olcys.api.dto;

import java.time.Instant;
import java.util.List;

public record RunDiffDto(
        String subjectId,
        String subjectDisplayName,
        String fromRunId,
        String toRunId,
        int fromRunNumber,
        int toRunNumber,
        Instant fromTimestamp,
        Instant toTimestamp,
        boolean walletRotationDetected,
        List<WalletRotationEventDto> walletRotations,
        List<String> addedAddresses,
        List<String> removedAddresses,
        List<AddressScoreChangeDto> scoreChanges,
        double volumeDeltaPln,
        List<CashOutSignalDto> newCashOutSignals,
        String summaryPl
) {
}
