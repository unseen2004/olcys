package pl.kas.olcys.connector.web3;

public record EntityTag(
        String address,
        String network,
        String name,
        EntityRole role,
        String jurisdiction,
        String notesPl
) {
}
