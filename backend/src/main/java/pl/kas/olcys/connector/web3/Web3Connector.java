package pl.kas.olcys.connector.web3;

import pl.kas.olcys.api.dto.AnalysisMode;

public interface Web3Connector {
    String network();
    RawOnChainProfile inspectAddress(String address, AnalysisMode mode, int snapshotStep);
}
