import React, { useState } from 'react';
import {
  AlertTriangle,
  Ban,
  Calculator,
  Check,
  Copy,
  Cpu,
  ExternalLink,
  FileCode2,
  FileDown,
  FileText,
  Scale,
  ShieldCheck,
  Stamp,
} from 'lucide-react';
import { getReportJsonUrl, getReportPdfUrl } from '../api/client';
import type { AnalysisRunDto } from '../types/api';
import {
  copyToClipboard,
  formatNumber,
  formatPln,
  formatUsd,
  formatUtcTimestamp,
  shortenAddress,
  shortenHash,
  translateAiProvider,
  translateCashOutSignalType,
  translateChecksumFormat,
  translateDirection,
  translateMode,
  translateRejectionCategory,
  translateSeverity,
} from '../utils/formatters';

interface OsintReportViewProps {
  run: AnalysisRunDto;
}

export const OsintReportView: React.FC<OsintReportViewProps> = ({ run }) => {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const report = run.report;

  const handleCopy = async (text: string, key: string) => {
    const ok = await copyToClipboard(text);
    if (ok) {
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 1800);
    }
  };

  if (!report) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-8 text-center text-sm text-[#64748B]">
        Brak wygenerowanego raportu dla tego uruchomienia analizy.
      </div>
    );
  }

  const pdfUrl = getReportPdfUrl(run.runId);
  const jsonUrl = getReportJsonUrl(run.runId);

  const zone1Facts = report.zone1SourceFacts ?? [];
  const zone2 = report.zone2Computations;
  const zone3 = report.zone3VerificationConclusions;

  return (
    <div className="space-y-6">
      {/* Official Report Masthead & Export Toolbar */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="bg-[#0F2942] text-white px-5 py-4 flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-sky-200">
              <Stamp className="w-3.5 h-3.5" />
              Raport Analityczny OSINT &amp; Blockchain • Krajowa Administracja Skarbowa
            </div>
            <h2 className="text-lg font-extrabold tracking-tight">
              {run.subjectDisplayName} — Analiza #{run.runNumber} (Snapshot v{run.snapshotStep})
            </h2>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-200">
              <span>Wygenerowano: {formatUtcTimestamp(report.generatedAt || run.createdAt)}</span>
              <span>Tryb: {translateMode(run.mode)}</span>
              <span className="font-mono text-[11px]">ID: {run.runId}</span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5 no-print">
            <a
              href={pdfUrl}
              download={`olcys-raport-kas-${run.runId}.pdf`}
              className="inline-flex items-center gap-2 rounded-lg bg-[#15803D] hover:bg-emerald-700 px-4 py-2.5 text-xs font-bold text-white shadow-xs transition-colors"
            >
              <FileDown className="w-4 h-4" />
              Pobierz Raport PDF (KAS)
            </a>
            <a
              href={jsonUrl}
              download={`olcys-dowod-${run.runId}.json`}
              className="inline-flex items-center gap-2 rounded-lg border border-white/30 bg-white/10 hover:bg-white/20 px-3.5 py-2.5 text-xs font-semibold text-white transition-colors"
            >
              <FileCode2 className="w-4 h-4" />
              Eksportuj Dowód JSON
            </a>
          </div>
        </div>

        {/* Executive Summary & Local AI Badge */}
        <div className="p-5 bg-[#F8FAFC] space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-xs font-bold uppercase tracking-wide text-[#0F2942]">
              Podsumowanie wykonawcze
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200 px-3 py-1 text-[11px] font-bold text-[#15803D]">
              <Cpu className="w-3.5 h-3.5" />
              AI On-Premise: {translateAiProvider(report.aiProviderUsed)}
            </span>
          </div>
          <p className="text-sm text-[#334155] leading-relaxed bg-white rounded-lg border border-slate-200 p-4">
            {report.executiveSummaryPl}
          </p>
        </div>
      </div>

      {/* STREFA 1: FAKTY ŹRÓDŁOWE (Chain-of-Custody) */}
      <section className="bg-white rounded-xl border-2 border-[#0F2942]/20 shadow-xs overflow-hidden">
        <div className="bg-[#0F2942]/5 border-b border-[#0F2942]/15 px-5 py-3.5 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#0F2942] text-white flex items-center justify-center font-bold text-sm">
              1
            </div>
            <div>
              <h3 className="text-sm font-extrabold uppercase tracking-wide text-[#0F2942] flex items-center gap-2">
                <FileText className="w-4 h-4" />
                STREFA 1: FAKTY ŹRÓDŁOWE (Chain-of-Custody)
              </h3>
              <p className="text-xs text-[#64748B]">
                Surowe, weryfikowalne materiały publiczne Web2 wraz z pełnym adresem URL, datą pobrania UTC oraz skrótem kryptograficznym SHA-256.
              </p>
            </div>
          </div>
          <span className="rounded-full bg-[#0F2942] px-3 py-1 text-xs font-bold text-white">
            Artefakty dowodowe: {zone1Facts.length}
          </span>
        </div>

        <div className="p-5 space-y-3">
          {zone1Facts.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-300 bg-[#F8FAFC] p-6 text-center text-xs text-[#64748B]">
              Brak pozyskanych artefaktów źródłowych Web2 w tej analizie.
            </div>
          ) : (
            zone1Facts.map((fact, idx) => (
              <div
                key={`${fact.evidenceId}-${idx}`}
                className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-4 space-y-2.5"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded bg-[#0F2942] px-2 py-0.5 text-[11px] font-bold text-white">
                      Dowód #{idx + 1}
                    </span>
                    <span className="text-sm font-bold text-[#0F2942]">{fact.sourceName}</span>
                    <span className="rounded border border-slate-300 bg-white px-2 py-0.5 text-[11px] text-[#334155]">
                      Dopasowanie: <strong>{fact.matchedIdentifier}</strong>
                    </span>
                  </div>
                  <span className="font-mono text-xs text-[#334155]">
                    {formatUtcTimestamp(fact.timestampUtc)}
                  </span>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-2 text-xs">
                  <div className="rounded border border-slate-200 bg-white px-3 py-2">
                    <div className="text-[10px] font-bold uppercase text-[#64748B] mb-0.5">
                      URL Źródła Publicznego
                    </div>
                    <a
                      href={fact.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-sky-800 hover:text-sky-600 underline break-all"
                    >
                      {fact.url}
                      <ExternalLink className="w-3 h-3 shrink-0" />
                    </a>
                  </div>

                  <div className="rounded border border-slate-200 bg-white px-3 py-2">
                    <div className="text-[10px] font-bold uppercase text-[#64748B] mb-0.5">
                      Integralność SHA-256 (Chain-of-Custody)
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <code className="font-mono text-[11px] text-[#0F2942] break-all">
                        {fact.sha256}
                      </code>
                      <button
                        type="button"
                        onClick={() => void handleCopy(fact.sha256, `sha-${fact.evidenceId}`)}
                        className="shrink-0 p-1 rounded hover:bg-slate-100 text-[#64748B] cursor-pointer"
                        title="Kopiuj pełny hash SHA-256"
                      >
                        {copiedKey === `sha-${fact.evidenceId}` ? (
                          <Check className="w-3.5 h-3.5 text-[#15803D]" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>
                  </div>
                </div>

                <div>
                  <div className="text-[10px] font-bold uppercase text-[#64748B] mb-1">
                    Zabezpieczony cytat / treść źródłowa
                  </div>
                  <blockquote className="rounded-md border-l-4 border-[#0F2942] bg-white px-3 py-2 text-xs text-[#334155] italic">
                    „{fact.quoteSnippet}”
                  </blockquote>
                </div>

                {fact.detectedAddresses && fact.detectedAddresses.length > 0 && (
                  <div className="flex flex-wrap items-center gap-1.5 pt-1">
                    <span className="text-[11px] font-semibold text-[#64748B]">
                      Wyekstrahowane adresy:
                    </span>
                    {fact.detectedAddresses.map((addr) => (
                      <code
                        key={addr}
                        className="rounded border border-slate-300 bg-white px-2 py-0.5 font-mono text-[11px] text-[#0F2942]"
                      >
                        {addr}
                      </code>
                    ))}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </section>

      {/* STREFA 2: WYNIKI OBLICZEŃ (Deterministyczna Punktacja i Metryki On-Chain) */}
      <section className="bg-white rounded-xl border-2 border-[#15803D]/25 shadow-xs overflow-hidden">
        <div className="bg-emerald-50/70 border-b border-emerald-200 px-5 py-3.5 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#15803D] text-white flex items-center justify-center font-bold text-sm">
              2
            </div>
            <div>
              <h3 className="text-sm font-extrabold uppercase tracking-wide text-[#0F2942] flex items-center gap-2">
                <Calculator className="w-4 h-4 text-[#15803D]" />
                STREFA 2: WYNIKI OBLICZEŃ (Deterministyczna Punktacja i Metryki On-Chain)
              </h3>
              <p className="text-xs text-[#64748B]">
                Powtarzalne obliczenia matematyczne i regułowe: sumy kontrolne, Confidence Score, Firewall FP oraz wolumeny on-chain.
              </p>
            </div>
          </div>
          <span className="rounded-full bg-[#15803D] px-3 py-1 text-xs font-bold text-white">
            100% Deterministyczne
          </span>
        </div>

        <div className="p-5 space-y-5">
          <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3.5 text-xs text-[#334155] leading-relaxed">
            <span className="font-bold text-[#0F2942]">Metodyka obliczeń: </span>
            {zone2.methodologySummaryPl}
          </div>

          {/* Zone 2 Summary Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="rounded-lg border border-slate-200 bg-white p-3.5">
              <div className="text-[11px] font-semibold uppercase text-[#64748B]">
                Łączny szacowany wolumen (PLN)
              </div>
              <div className="text-xl font-extrabold text-[#0F2942] tabular-nums mt-1">
                {formatPln(zone2.totalEstimatedVolumePln)}
              </div>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-3.5">
              <div className="text-[11px] font-semibold uppercase text-[#64748B]">
                Wolumen Stablecoin (USD)
              </div>
              <div className="text-xl font-extrabold text-[#15803D] tabular-nums mt-1">
                {formatUsd(zone2.totalStablecoinVolumeUsd)}
              </div>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-3.5">
              <div className="text-[11px] font-semibold uppercase text-[#64748B]">
                Przeanalizowane transakcje
              </div>
              <div className="text-xl font-extrabold text-[#0F2942] tabular-nums mt-1">
                {formatNumber(zone2.totalTransactionsAnalyzed)}
              </div>
            </div>
          </div>

          {/* Confirmed Wallets Summary */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold uppercase tracking-wide text-[#15803D] flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4" />
              Portfele niehostowane zatwierdzone w Strefie 2 ({zone2.confirmedWallets?.length ?? 0})
            </h4>
            {(zone2.confirmedWallets ?? []).length === 0 ? (
              <p className="text-xs text-[#64748B] italic">
                Brak portfeli niehostowanych zakwalifikowanych powyżej progu odrzucenia.
              </p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#F8FAFC] border-b border-slate-200 text-[11px] uppercase text-[#64748B]">
                    <tr>
                      <th className="px-3 py-2">Sieć</th>
                      <th className="px-3 py-2">Adres portfela</th>
                      <th className="px-3 py-2">Walidacja kryptograficzna</th>
                      <th className="px-3 py-2">Wynik i uzasadnienie punktowe</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {zone2.confirmedWallets.map((w) => (
                      <tr key={w.id} className="bg-white align-top">
                        <td className="px-3 py-2.5 font-bold text-[#0F2942]">{w.network}</td>
                        <td className="px-3 py-2.5 font-mono text-[11px] text-[#0F2942] break-all">
                          {w.address}
                        </td>
                        <td className="px-3 py-2.5 whitespace-nowrap">
                          {translateChecksumFormat(w.checksumFormat)}
                        </td>
                        <td className="px-3 py-2.5 space-y-1">
                          <div className="font-bold text-[#15803D]">
                            {w.confidenceScore}% ({w.confidenceLevelPl})
                          </div>
                          <ul className="space-y-0.5 text-[11px] text-[#334155]">
                            {w.scoreBreakdown.map((sb, i) => (
                              <li key={`${sb.ruleId}-${i}`}>
                                • <strong>{sb.delta >= 0 ? `+${sb.delta}` : sb.delta} pkt</strong> —{' '}
                                {sb.label}: {sb.explanationPl}
                              </li>
                            ))}
                          </ul>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Rejected False Positives Summary */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold uppercase tracking-wide text-[#B91C1C] flex items-center gap-1.5">
              <Ban className="w-4 h-4" />
              Odrzucone False Positives — wynik 0% ({zone2.rejectedFalsePositives?.length ?? 0})
            </h4>
            {(zone2.rejectedFalsePositives ?? []).length === 0 ? (
              <p className="text-xs text-[#64748B] italic">
                Brak adresów wykluczonych przez Firewall FP.
              </p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-red-200">
                <table className="w-full text-left text-xs">
                  <thead className="bg-red-50/70 border-b border-red-200 text-[11px] uppercase text-[#B91C1C]">
                    <tr>
                      <th className="px-3 py-2">Sieć</th>
                      <th className="px-3 py-2">Adres (wykluczony)</th>
                      <th className="px-3 py-2">Kategoria / Podmiot</th>
                      <th className="px-3 py-2">Powód wykluczenia</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-red-100">
                    {zone2.rejectedFalsePositives.map((fp) => (
                      <tr key={fp.id} className="bg-white align-top">
                        <td className="px-3 py-2 font-bold text-[#0F2942]">{fp.network}</td>
                        <td className="px-3 py-2 font-mono text-[11px] text-[#B91C1C] line-through break-all">
                          {fp.address}
                        </td>
                        <td className="px-3 py-2">
                          <div className="font-semibold text-[#B91C1C]">
                            {translateRejectionCategory(fp.rejectionCategory)}
                          </div>
                          {fp.entityTag && (
                            <div className="text-[11px] text-[#334155]">{fp.entityTag}</div>
                          )}
                          {fp.interactionRecorded && (
                            <span className="inline-block mt-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-[#B45309]">
                              Odnotowano interakcję on-chain
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2 text-[#334155]">
                          {fp.rejectionReason ?? 'Odrzucono przez Firewall FP'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Chronological Flow inside Zone 2 */}
          {zone2.chronologicalFlow && zone2.chronologicalFlow.length > 0 && (
            <div className="space-y-2">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#334155]">
                Zestawienie chronologiczne przepływów on-chain ({zone2.chronologicalFlow.length})
              </h4>
              <div className="overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#F8FAFC] border-b border-slate-200 text-[11px] uppercase text-[#64748B]">
                    <tr>
                      <th className="px-3 py-2">Data UTC</th>
                      <th className="px-3 py-2">Kierunek</th>
                      <th className="px-3 py-2">Hash TX</th>
                      <th className="px-3 py-2">Strona przeciwna</th>
                      <th className="px-3 py-2 text-right">Kwota</th>
                      <th className="px-3 py-2 text-right">Wartość PLN</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {zone2.chronologicalFlow.map((ev, i) => {
                      const dir = translateDirection(ev.direction);
                      return (
                        <tr key={`${ev.txHash}-${i}`} className="bg-white">
                          <td className="px-3 py-2 font-mono text-[11px] whitespace-nowrap">
                            {formatUtcTimestamp(ev.timestamp)}
                          </td>
                          <td className="px-3 py-2 whitespace-nowrap">
                            <span
                              className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                                dir.isOutflow
                                  ? 'bg-red-50 text-[#B91C1C]'
                                  : 'bg-emerald-50 text-[#15803D]'
                              }`}
                            >
                              {dir.label}
                            </span>
                          </td>
                          <td className="px-3 py-2 font-mono text-[11px] whitespace-nowrap">
                            {shortenHash(ev.txHash, 8, 6)}
                          </td>
                          <td className="px-3 py-2">
                            {ev.counterpartyTag ||
                              shortenAddress(dir.isOutflow ? ev.toAddress : ev.fromAddress)}
                          </td>
                          <td className="px-3 py-2 text-right font-mono tabular-nums whitespace-nowrap">
                            {formatNumber(ev.amount)} {ev.asset}
                          </td>
                          <td className="px-3 py-2 text-right font-bold tabular-nums whitespace-nowrap">
                            {formatPln(ev.estimatedPln)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* STREFA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI */}
      <section className="bg-white rounded-xl border-2 border-[#B45309]/35 shadow-xs overflow-hidden">
        <div className="bg-amber-50 border-b border-amber-200 px-5 py-3.5 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#B45309] text-white flex items-center justify-center font-bold text-sm">
              3
            </div>
            <div>
              <h3 className="text-sm font-extrabold uppercase tracking-wide text-[#0F2942] flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-[#B45309]" />
                STREFA 3: WNIOSKI WYMAGAJĄCE DALSZEJ WERYFIKACJI (Sygnały Fiat Cash-Out i Ryzyka PIT-38)
              </h3>
              <p className="text-xs text-[#64748B]">
                Hipotezy analityczne i wskazania operacyjne wymagające potwierdzenia w toku czynności sprawdzających lub postępowania podatkowego KAS.
              </p>
            </div>
          </div>
          <span className="rounded-full bg-[#B45309] px-3 py-1 text-xs font-bold text-white">
            Wymaga weryfikacji organu
          </span>
        </div>

        <div className="p-5 space-y-5">
          {/* Cash-Out Signals Cards */}
          <div className="space-y-2.5">
            <h4 className="text-xs font-bold uppercase tracking-wide text-[#0F2942]">
              Zidentyfikowane sygnały Fiat Cash-Out ({zone3.cashOutSignals?.length ?? 0})
            </h4>
            {(zone3.cashOutSignals ?? []).length === 0 ? (
              <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-4 text-xs text-[#64748B]">
                Nie odnotowano bezpośrednich sygnałów wypłaty Fiat Cash-Out na zatwierdzonych portfelach.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {zone3.cashOutSignals.map((sig) => {
                  const sigType = translateCashOutSignalType(sig.signalType);
                  const sev = translateSeverity(sig.severity);
                  return (
                    <div
                      key={sig.id}
                      className="rounded-lg border border-amber-200 bg-amber-50/40 p-4 space-y-2"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className={`rounded border px-2 py-0.5 text-[11px] font-bold ${sigType.badgeClass}`}>
                          {sigType.label}
                        </span>
                        <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${sev.badgeClass}`}>
                          {sev.label}
                        </span>
                      </div>

                      <div className="flex items-baseline justify-between gap-2">
                        <span className="text-xs font-bold text-[#0F2942]">
                          {sig.counterpartyName} ({sig.network})
                        </span>
                        <span className="text-sm font-extrabold text-[#B91C1C] tabular-nums">
                          {formatPln(sig.estimatedPln)}
                        </span>
                      </div>

                      <p className="text-xs text-[#334155] leading-relaxed">{sig.descriptionPl}</p>

                      <div className="rounded-md border border-amber-300 bg-white p-2.5 text-xs text-[#0F2942]">
                        <span className="font-bold text-[#B45309]">Podpowiedź weryfikacyjna KAS: </span>
                        {sig.verificationHintPl}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* PIT-38 Risk Indicators & Recommendations */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="rounded-lg border border-red-200 bg-red-50/30 p-4 space-y-2">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#B91C1C]">
                Wskaźniki ryzyka podatkowego (PIT-38 / art. 30b ustawy o PIT)
              </h4>
              <ul className="space-y-1.5 text-xs text-[#334155]">
                {(zone3.pit38RiskIndicatorsPl ?? []).length === 0 ? (
                  <li className="text-[#64748B] italic">Brak wykrytych wskaźników ryzyka PIT-38.</li>
                ) : (
                  zone3.pit38RiskIndicatorsPl.map((item, idx) => (
                    <li key={idx} className="flex items-start gap-2">
                      <span className="mt-1 w-1.5 h-1.5 rounded-full bg-[#B91C1C] shrink-0" />
                      <span>{item}</span>
                    </li>
                  ))
                )}
              </ul>
            </div>

            <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-4 space-y-2">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#0F2942]">
                Zalecenia do dalszych czynności dochodzeniowych
              </h4>
              <ul className="space-y-1.5 text-xs text-[#334155]">
                {(zone3.investigativeRecommendationsPl ?? []).length === 0 ? (
                  <li className="text-[#64748B] italic">Brak dodatkowych zaleceń.</li>
                ) : (
                  zone3.investigativeRecommendationsPl.map((rec, idx) => (
                    <li key={idx} className="flex items-start gap-2">
                      <span className="mt-1 w-1.5 h-1.5 rounded-full bg-[#0F2942] shrink-0" />
                      <span>{rec}</span>
                    </li>
                  ))
                )}
              </ul>
            </div>
          </div>

          {/* Legal & Methodological Disclaimers */}
          <div className="rounded-lg border border-slate-300 bg-slate-50 p-4 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-[#334155]">
              <Scale className="w-4 h-4 text-[#0F2942]" />
              Zastrzeżenia prawne i ograniczenia metodyczne
            </div>
            <ul className="space-y-1 text-xs text-[#64748B]">
              {(zone3.limitationsAndLegalDisclaimerPl ?? []).map((note, idx) => (
                <li key={idx}>• {note}</li>
              ))}
            </ul>
          </div>
        </div>
      </section>
    </div>
  );
};
