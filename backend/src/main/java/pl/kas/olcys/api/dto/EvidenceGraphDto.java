package pl.kas.olcys.api.dto;

import java.util.List;

public record EvidenceGraphDto(
        List<GraphNodeDto> nodes,
        List<GraphEdgeDto> edges
) {
}
