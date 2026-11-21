import React, { useMemo, useState } from 'react';
import {
  Activity,
  Ban,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  FileSearch,
  Lock,
  ShieldAlert,
  ShieldCheck,
  Wallet,
} from 'lucide-react';
import type {
  AddressFindingDto,
  AnalysisRunDto,
  EvidenceArtifactDto,
} from '../types/api';
import {
  copyToClipboard,
  formatNumber,
  formatPln,
  formatUsd,
  formatUtcTimestamp,
  getExplorerUrl,
  shortenHash,
  translateChecksumFormat,
  translateContextClassification,
  translateRejectionCategory,
} from '../utils/formatters';

interface AddressCardsProps {
  run: AnalysisRunDto;
}

function getScoreTheme(score: number): {
  barClass: string;
  badgeClass: string;
  borderClass: string;
} {
  if (score >= 80) {
    return {
      barClass: 'bg-[#15803D]',
      badgeClass: 'bg-emerald-100 text-[#15803D] border-emerald-300',
      borderClass: 'border-emerald-200',
    };
  }
  if (score >= 60) {
    return {
      barClass: 'bg-[#0F2942]',
      badgeClass: 'bg-sky-100 text-[#0F2942] border-sky-300',
      borderClass: 'border-sky-200',
    };
  }
  if (score >= 40) {
    return {
      barClass: 'bg-[#B45309]',
      badgeClass: 'bg-amber-100 text-[#B45309] border-amber-300',
      borderClass: 'border-amber-200',
    };
  }
  return {
    barClass: 'bg-[#B91C1C]',
    badgeClass: 'bg-red-100 text-[#B91C1C] border-red-300',
    borderClass: 'border-red-200',
  };
}

export const AddressCards: React.FC<AddressCardsProps> = ({ run }) => {
  const [copiedAddr, setCopiedAddr] = useState<string | null>(null);

  const evidenceMap = useMemo(() => {
    const map = new Map<string, EvidenceArtifactDto>();
    for (const ev of run.evidences ?? []) {
      map.set(ev.id, ev);
    }
    return map;
  }, [run.evidences]);

  const sortedConfirmed = useMemo(
    () =>
      [...(run.confirmedAddresses ?? [])].sort(
        (a, b) => b.confidenceScore - a.confidenceScore,
      ),
    [run.confirmedAddresses],
  );

  const rejectedList = useMemo(
    () => run.rejectedAddresses ?? [],
    [run.rejectedAddresses],
  );

  const handleCopy = async (value: string) => {
    const ok = await copyToClipboard(value);
    if (ok) {
      setCopiedAddr(value);
      setTimeout(() => setCopiedAddr(null), 1800);
    }
  };

  const resolveEvidences = (ids: string[] | undefined): EvidenceArtifactDto[] => {
    if (!ids || ids.length === 0) return [];
    const seen = new Set<string>();
    const list: EvidenceArtifactDto[] = [];
    for (const id of ids) {
      if (seen.has(id)) continue;
      seen.add(id);
      const found = evidenceMap.get(id);
      if (found) list.push(found);
    }
    return list;
  };

  const renderEvidenceLinks = (finding: AddressFindingDto) => {
    const items = resolveEvidences(finding.evidenceIds);
    if (items.length === 0) {
      return (
        <div className="text-xs text-[#64748B] italic">
          Brak przypisanych bezpośrednich artefaktów URL.
        </div>
      );
    }

    return (
      <div className="space-y-1.5">
        {items.map((ev) => {
          const ctx = translateContextClassification(ev.contextClassification);
          return (
            <div
              key={ev.id}
              className="rounded-lg border border-slate-200 bg-[#F8FAFC] px-3 py-2 flex flex-col gap-1"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-[#0F2942]">{ev.sourceName}</span>
                  <span className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${ctx.badgeClass}`}>
                    {ctx.label}
                  </span>
                </div>
                <span className="font-mono text-[10px] text-[#64748B]">
                  {formatUtcTimestamp(ev.fetchedAt)}
                </span>
              </div>

              <a
                href={ev.url}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-xs text-sky-800 hover:text-sky-600 underline break-all"
              >
                {ev.url}
                <ExternalLink className="w-3 h-3 shrink-0" />
              </a>

              <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-[#64748B]">
                <span className="italic truncate max-w-xl" title={ev.snippet}>
                  „{ev.snippet}”
                </span>
                <span className="font-mono text-[10px]" title={ev.sha256}>
                  SHA-256: {shortenHash(ev.sha256, 8, 6)}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="space-y-8">
      {/* SECTION 1: Confirmed Unhosted Wallets */}
      <section className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-lg bg-emerald-100 text-[#15803D] flex items-center justify-center">
              <Wallet className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#0F2942]">
                Zidentyfikowane portfele niehostowane (Unhosted Wallets)
              </h2>
              <p className="text-xs text-[#64748B]">
                Adresy pozytywnie zweryfikowane kryptograficznie, przepuszczone przez Firewall FP i ocenione deterministyczną punktacją KAS (0–100%).
              </p>
            </div>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-1 text-xs font-bold text-[#15803D]">
            <ShieldCheck className="w-4 h-4" />
            Zatwierdzone: {sortedConfirmed.length}
          </span>
        </div>

        {sortedConfirmed.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center">
            <FileSearch className="w-9 h-9 text-slate-300 mx-auto mb-2" />
            <p className="text-sm font-bold text-[#0F2942]">
              Brak zidentyfikowanych portfeli niehostowanych
            </p>
            <p className="text-xs text-[#64748B] mt-1 max-w-lg mx-auto">
              System nie przypisał podmiotowi żadnego prywatnego portfela niehostowanego (wszystkie znalezione adresy mogły zostać wykluczone przez Firewall FP lub źródła zawierały jedynie szum).
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {sortedConfirmed.map((wallet) => {
              const theme = getScoreTheme(wallet.confidenceScore);
              const explorerUrl = getExplorerUrl(wallet.network, wallet.address, 'address');

              return (
                <article
                  key={wallet.id}
                  className={`bg-white rounded-xl border-2 ${theme.borderClass} shadow-xs overflow-hidden`}
                >
                  {/* Top Header */}
                  <div className="p-4 sm:p-5 bg-[#F8FAFC] border-b border-slate-200 space-y-3">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="space-y-1.5 min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="rounded-md bg-[#0F2942] px-2.5 py-0.5 text-xs font-bold text-white">
                            {wallet.network}
                          </span>
                          <span className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-2 py-0.5 text-[11px] font-semibold text-[#334155]">
                            <Lock className="w-3 h-3 text-[#15803D]" />
                            {translateChecksumFormat(wallet.checksumFormat)}
                          </span>
                          {wallet.checksumValid && (
                            <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 border border-emerald-200 px-2 py-0.5 text-[11px] font-semibold text-[#15803D]">
                              <CheckCircle2 className="w-3 h-3" />
                              Suma kontrolna poprawna
                            </span>
                          )}
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          <code className="font-mono text-sm sm:text-base font-bold text-[#0F2942] break-all">
                            {wallet.address}
                          </code>
                          <button
                            type="button"
                            onClick={() => void handleCopy(wallet.address)}
                            className="inline-flex items-center gap-1 rounded border border-slate-300 bg-white px-2 py-1 text-[11px] font-medium text-[#334155] hover:bg-slate-50 cursor-pointer"
                          >
                            {copiedAddr === wallet.address ? (
                              <>
                                <Check className="w-3 h-3 text-[#15803D]" />
                                Skopiowano
                              </>
                            ) : (
                              <>
                                <Copy className="w-3 h-3" />
                                Kopiuj
                              </>
                            )}
                          </button>
                          {explorerUrl && (
                            <a
                              href={explorerUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="inline-flex items-center gap-1 rounded border border-slate-300 bg-white px-2 py-1 text-[11px] font-medium text-[#0F2942] hover:bg-slate-50"
                            >
                              Eksplorator
                              <ExternalLink className="w-3 h-3" />
                            </a>
                          )}
                        </div>
                      </div>

                      <div className="text-right shrink-0">
                        <div className="text-[11px] font-semibold uppercase tracking-wide text-[#64748B]">
                          Wskaźnik Pewności (Confidence Score)
                        </div>
                        <div className="flex items-center justify-end gap-2 mt-1">
                          <span className={`rounded-full border px-2.5 py-0.5 text-xs font-bold ${theme.badgeClass}`}>
                            {wallet.confidenceLevelPl}
                          </span>
                          <span className="text-2xl font-extrabold text-[#0F2942] tabular-nums">
                            {wallet.confidenceScore}%
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Progress bar 0-100% */}
                    <div className="space-y-1">
                      <div className="h-3 w-full rounded-full bg-slate-200 overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${theme.barClass}`}
                          style={{ width: `${Math.min(100, Math.max(0, wallet.confidenceScore))}%` }}
                        />
                      </div>
                      <div className="flex justify-between text-[10px] font-medium text-[#64748B]">
                        <span>0% (Wykluczone)</span>
                        <span>40% (Dyskusja forum)</span>
                        <span>60% (Kontekst transakcyjny)</span>
                        <span>80–100% (Własny profil / Synergia)</span>
                      </div>
                    </div>
                  </div>

                  {/* Body: Score Breakdown + On-Chain Snapshot + Evidence Links */}
                  <div className="p-4 sm:p-5 space-y-5">
                    {/* Score Breakdown Table */}
                    <div className="space-y-2">
                      <h3 className="text-xs font-bold uppercase tracking-wide text-[#334155]">
                        Deterministyczne rozbicie punktowe (metodyka KAS)
                      </h3>
                      <div className="overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-[#F8FAFC] border-b border-slate-200 text-[11px] uppercase text-[#64748B]">
                            <tr>
                              <th className="px-3 py-2 font-semibold">Reguła / Kryterium</th>
                              <th className="px-3 py-2 font-semibold">Uzasadnienie</th>
                              <th className="px-3 py-2 font-semibold text-right">Wpływ (pkt)</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-200">
                            {(wallet.scoreBreakdown ?? []).map((item, idx) => (
                              <tr key={`${item.ruleId}-${idx}`} className="bg-white">
                                <td className="px-3 py-2 font-semibold text-[#0F2942] whitespace-nowrap">
                                  {item.label}
                                </td>
                                <td className="px-3 py-2 text-[#334155]">{item.explanationPl}</td>
                                <td className="px-3 py-2 text-right font-bold tabular-nums whitespace-nowrap">
                                  <span
                                    className={`inline-block rounded px-2 py-0.5 ${
                                      item.delta >= 0
                                        ? 'bg-emerald-50 text-[#15803D]'
                                        : 'bg-red-50 text-[#B91C1C]'
                                    }`}
                                  >
                                    {item.delta >= 0 ? `+${item.delta}` : item.delta} pkt
                                  </span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>

                    {/* On-Chain summary strip if present */}
                    {wallet.onChainMetrics && (
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-lg border border-slate-200 bg-[#F8FAFC] p-3 text-xs">
                        <div>
                          <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                            Transakcje on-chain
                          </div>
                          <div className="text-sm font-bold text-[#0F2942] tabular-nums mt-0.5">
                            {formatNumber(wallet.onChainMetrics.txCount)}
                          </div>
                        </div>
                        <div>
                          <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                            Szac. wolumen (PLN)
                          </div>
                          <div className="text-sm font-bold text-[#0F2942] tabular-nums mt-0.5">
                            {formatPln(wallet.onChainMetrics.estimatedTotalVolumePln)}
                          </div>
                        </div>
                        <div>
                          <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                            Wolumen Stablecoin
                          </div>
                          <div className="text-sm font-bold text-[#0F2942] tabular-nums mt-0.5">
                            {formatUsd(wallet.onChainMetrics.stablecoinVolumeUsd)}
                          </div>
                        </div>
                        <div>
                          <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                            Przepływ natywny
                          </div>
                          <div className="text-sm font-bold text-[#0F2942] tabular-nums mt-0.5">
                            +{formatNumber(wallet.onChainMetrics.totalInflowNative)} / -
                            {formatNumber(wallet.onChainMetrics.totalOutflowNative)}{' '}
                            {wallet.onChainMetrics.nativeSymbol}
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Evidence links */}
                    <div className="space-y-2">
                      <h3 className="text-xs font-bold uppercase tracking-wide text-[#334155]">
                        Źródła dowodowe potwierdzające powiązanie ({wallet.evidenceIds?.length ?? 0})
                      </h3>
                      {renderEvidenceLinks(wallet)}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {/* SECTION 2: False Positives Firewall */}
      <section className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-lg bg-red-100 text-[#B91C1C] flex items-center justify-center">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#0F2942]">
                Odrzucone trafienia — Ochrona przed błędnym przypisaniem (Firewall False Positives)
              </h2>
              <p className="text-xs text-[#64748B]">
                Adresy giełd CEX, smart kontraktów (`bytecode ≠ 0x`), mikserów i zbiórek automatycznie wykluczone z przypisania osobistego (`0%`).
              </p>
            </div>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 border border-red-200 px-3 py-1 text-xs font-bold text-[#B91C1C]">
            <Ban className="w-4 h-4" />
            Wykluczone FP: {rejectedList.length}
          </span>
        </div>

        {rejectedList.length === 0 ? (
          <div className="rounded-xl border border-slate-200 bg-white p-6 text-center text-xs text-[#64748B]">
            W tej analizie nie napotkano adresów wymagających wykluczenia przez Firewall False Positives.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {rejectedList.map((fp) => {
              const explorerUrl = getExplorerUrl(fp.network, fp.address, 'address');

              return (
                <article
                  key={fp.id}
                  className="bg-white rounded-xl border border-red-200 shadow-xs overflow-hidden"
                >
                  <div className="bg-red-50/70 border-b border-red-200 px-4 py-3 flex flex-wrap items-center justify-between gap-3">
                    <div className="space-y-1 min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded bg-[#B91C1C] px-2 py-0.5 text-[11px] font-bold text-white">
                          0% • ODRZUCONO FP
                        </span>
                        <span className="rounded bg-white border border-red-200 px-2 py-0.5 text-[11px] font-bold text-[#B91C1C]">
                          {translateRejectionCategory(fp.rejectionCategory)}
                        </span>
                        <span className="rounded bg-slate-800 px-2 py-0.5 text-[11px] font-semibold text-white">
                          {fp.network}
                        </span>
                        <span className="rounded bg-white border border-slate-200 px-2 py-0.5 text-[11px] text-[#334155]">
                          {translateChecksumFormat(fp.checksumFormat)}
                        </span>
                        {fp.interactionRecorded && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 border border-amber-300 px-2.5 py-0.5 text-[11px] font-bold text-[#B45309]">
                            <Activity className="w-3.5 h-3.5" />
                            Odnotowano interakcję on-chain
                          </span>
                        )}
                      </div>

                      <div className="flex flex-wrap items-center gap-2 pt-0.5">
                        <code className="font-mono text-sm font-bold text-[#B91C1C] line-through decoration-2 break-all">
                          {fp.address}
                        </code>
                        <button
                          type="button"
                          onClick={() => void handleCopy(fp.address)}
                          className="inline-flex items-center gap-1 rounded border border-slate-300 bg-white px-2 py-0.5 text-[11px] text-[#334155] hover:bg-slate-50 cursor-pointer"
                        >
                          {copiedAddr === fp.address ? (
                            <Check className="w-3 h-3 text-[#15803D]" />
                          ) : (
                            <Copy className="w-3 h-3" />
                          )}
                          Kopiuj
                        </button>
                        {explorerUrl && (
                          <a
                            href={explorerUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 rounded border border-slate-300 bg-white px-2 py-0.5 text-[11px] text-[#0F2942] hover:bg-slate-50"
                          >
                            Eksplorator
                            <ExternalLink className="w-3 h-3" />
                          </a>
                        )}
                      </div>
                    </div>

                    {fp.entityTag && (
                      <div className="rounded-lg border border-red-200 bg-white px-3 py-1.5 text-right">
                        <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                          Zidentyfikowany podmiot / kontrakt
                        </div>
                        <div className="text-xs font-bold text-[#0F2942]">{fp.entityTag}</div>
                      </div>
                    )}
                  </div>

                  <div className="p-4 space-y-3">
                    <div className="rounded-lg border border-red-100 bg-red-50/40 p-3 text-xs text-[#334155]">
                      <span className="font-bold text-[#B91C1C]">Uzasadnienie odrzucenia: </span>
                      <span>
                        {fp.rejectionReason ??
                          'Adres wykluczony przez deterministyczną zaporę False Positive Firewall.'}
                      </span>
                      {fp.onChainMetrics?.isSmartContract && fp.onChainMetrics.bytecodePrefix && (
                        <div className="mt-1.5 font-mono text-[11px] text-[#B91C1C]">
                          eth_getCode bytecode: {fp.onChainMetrics.bytecodePrefix} (bytecode != 0x)
                        </div>
                      )}
                    </div>

                    {fp.interactionRecorded && (
                      <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2 text-xs text-[#B45309]">
                        <span className="font-bold">Znaczenie dowodowe: </span>
                        Adres nie należy do podmiotu, jednak potwierdzony portfel niehostowany podmiotu wchodził z tym adresem w bezpośrednią interakcję on-chain (istotne dla analizy przepływów Cash-Out / DeFi).
                      </div>
                    )}

                    <div className="space-y-1.5">
                      <div className="text-[11px] font-bold uppercase tracking-wide text-[#64748B]">
                        Miejsce wystąpienia w źródłach OSINT
                      </div>
                      {renderEvidenceLinks(fp)}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
};
