package pl.kas.olcys.api.dto;

public record GraphEdgeDto(
        String id,
        String source,
        String target,
        String label,
        boolean rejected
) {
}
