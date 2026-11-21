package pl.kas.olcys.connector.web2;

import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.IdentifierDto;

import java.util.List;

public interface Web2Connector {
    String name();
    boolean supports(AnalysisMode mode);
    List<RawEvidence> search(List<IdentifierDto> identifiers, int snapshotStep);
}
