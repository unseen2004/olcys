import type { AnalysisMode } from '../types/api';

const plnFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const usdFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const numberFormatter = new Intl.NumberFormat('pl-PL', {
  maximumFractionDigits: 6,
});

export function formatPln(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0,00 zł';
  }
  return plnFormatter.format(value);
}

export function formatSignedPln(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0,00 zł';
  }
  const formatted = plnFormatter.format(Math.abs(value));
  if (value > 0) return `+${formatted}`;
  if (value < 0) return `-${formatted}`;
  return formatted;
}

export function formatUsd(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0,00 USD';
  }
  return usdFormatter.format(value);
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '0';
  }
  return numberFormatter.format(value);
}

export function formatUtcTimestamp(iso: string | null | undefined): string {
  if (!iso) return 'Brak daty UTC';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  const y = date.getUTCFullYear();
  const m = pad(date.getUTCMonth() + 1);
  const d = pad(date.getUTCDate());
  const hh = pad(date.getUTCHours());
  const mm = pad(date.getUTCMinutes());
  const ss = pad(date.getUTCSeconds());
  return `${y}-${m}-${d} ${hh}:${mm}:${ss} UTC`;
}

export function formatUtcDateShort(iso: string | null | undefined): string {
  if (!iso) return 'Brak daty';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export function shortenAddress(address: string | null | undefined, head = 8, tail = 6): string {
  if (!address) return '—';
  if (address.length <= head + tail + 3) return address;
  return `${address.slice(0, head)}…${address.slice(-tail)}`;
}

export function shortenHash(hash: string | null | undefined, head = 10, tail = 8): string {
  if (!hash) return '—';
  if (hash.length <= head + tail + 3) return hash;
  return `${hash.slice(0, head)}…${hash.slice(-tail)}`;
}

export const IDENTIFIER_OPTIONS: Array<{
  value: string;
  label: string;
  placeholder: string;
}> = [
  { value: 'NICK', label: 'Nick / Pseudonim', placeholder: 'np. krypto_janek' },
  { value: 'FULL_NAME', label: 'Imię i nazwisko', placeholder: 'np. Jan Kowalski' },
  { value: 'EMAIL', label: 'Adres e-mail', placeholder: 'np. janek.dev@example.pl' },
  { value: 'TELEGRAM_ID', label: 'ID komunikatora Telegram', placeholder: 'np. @janek_otc' },
  { value: 'SOCIAL_LOGIN', label: 'Login społecznościowy', placeholder: 'np. u/krypto_janek' },
  { value: 'CRYPTO_ADDRESS', label: 'Adres kryptowalutowy (Reverse OSINT)', placeholder: 'np. 0x... lub bc1...' },
];

export function translateIdentifierType(type: string): string {
  switch (type.toUpperCase()) {
    case 'NICK':
      return 'Nick / Pseudonim';
    case 'FULL_NAME':
      return 'Imię i nazwisko';
    case 'EMAIL':
      return 'Adres e-mail';
    case 'TELEGRAM_ID':
      return 'ID Telegram';
    case 'SOCIAL_LOGIN':
      return 'Login społecznościowy';
    case 'CRYPTO_ADDRESS':
      return 'Adres krypto';
    default:
      return type;
  }
}

export function translateMode(mode: AnalysisMode | string): string {
  switch (mode) {
    case 'HYBRID':
      return 'Hybrydowy (Live + Walidacyjny)';
    case 'LIVE':
      return 'Live OSINT (Publiczne API)';
    case 'SYNTHETIC':
      return 'Scenariusz Walidacyjny KAS';
    default:
      return mode;
  }
}

export function translateChecksumFormat(format: string | null | undefined): string {
  if (!format) return 'Nieznany format';
  switch (format.toUpperCase()) {
    case 'EIP-55':
      return 'EIP-55 (Keccak-256)';
    case 'BECH32':
      return 'Bech32 (SegWit)';
    case 'BECH32M':
      return 'Bech32m (Taproot)';
    case 'BASE58CHECK':
      return 'Base58Check (Double SHA-256)';
    case 'TRON_BASE58CHECK':
      return 'TRON Base58Check';
    default:
      return format;
  }
}

export function translateRejectionCategory(category: string | null | undefined): string {
  if (!category) return 'Wykluczenie regułowe';
  switch (category.toUpperCase()) {
    case 'CEX':
      return 'Giełda scentralizowana (CEX / CASP)';
    case 'CASP_P2P':
      return 'Podmiot CASP / Kantor P2P';
    case 'SMART_CONTRACT':
      return 'Smart Kontrakt (bytecode ≠ 0x)';
    case 'DEX_ROUTER':
      return 'Router DEX / Protokół DeFi';
    case 'MIXER':
      return 'Mikser kryptowalutowy';
    case 'CHARITY':
      return 'Zbiórka charytatywna / Fundacja';
    case 'BURN':
      return 'Adres spalania (Burn)';
    case 'STABLECOIN':
      return 'Kontrakt emisji Stablecoin';
    case 'SUSPECTED_CUSTODIAL':
      return 'Podejrzenie adresu powierniczego (>10 tys. tx)';
    default:
      return category;
  }
}

export function translateCashOutSignalType(type: string | null | undefined): {
  label: string;
  badgeClass: string;
} {
  switch ((type ?? '').toUpperCase()) {
    case 'CEX_OFFRAMP':
      return {
        label: 'CEX Off-Ramp (Transfer na giełdę)',
        badgeClass: 'bg-red-50 text-[#B91C1C] border-red-200',
      };
    case 'STABLECOIN_CONVERSION':
      return {
        label: 'Konwersja Stablecoin (USDT/USDC/DAI)',
        badgeClass: 'bg-amber-50 text-[#B45309] border-amber-200',
      };
    case 'P2P_OR_GATEWAY':
      return {
        label: 'Korelacja P2P / Bramka krypto-fiat',
        badgeClass: 'bg-orange-50 text-orange-800 border-orange-200',
      };
    case 'RECURRING_CASHOUT':
      return {
        label: 'Cykliczna wypłata środków',
        badgeClass: 'bg-rose-50 text-[#B91C1C] border-rose-200',
      };
    case 'AML_STRUCTURING_OR_2HOP':
      return {
        label: 'Przepływ 2-Hop / Dzielenie transz (AML)',
        badgeClass: 'bg-purple-50 text-purple-900 border-purple-200',
      };
    default:
      return {
        label: type ? type : 'Sygnał przepływu',
        badgeClass: 'bg-slate-100 text-[#334155] border-slate-200',
      };
  }
}

export function translateSeverity(severity: string | null | undefined): {
  label: string;
  badgeClass: string;
} {
  switch ((severity ?? '').toUpperCase()) {
    case 'HIGH':
    case 'CRITICAL':
    case 'WYSOKIE':
      return {
        label: 'Priorytet WYSOKI',
        badgeClass: 'bg-red-100 text-[#B91C1C] border-red-300',
      };
    case 'MEDIUM':
    case 'MODERATE':
    case 'ŚREDNIE':
      return {
        label: 'Priorytet ŚREDNI',
        badgeClass: 'bg-amber-100 text-[#B45309] border-amber-300',
      };
    case 'LOW':
    case 'NISKIE':
      return {
        label: 'Priorytet NISKI',
        badgeClass: 'bg-slate-100 text-[#334155] border-slate-300',
      };
    default:
      return {
        label: severity || 'Do weryfikacji',
        badgeClass: 'bg-amber-50 text-[#B45309] border-amber-200',
      };
  }
}

export function translateContextClassification(context: string | null | undefined): {
  label: string;
  badgeClass: string;
} {
  const upper = (context ?? '').toUpperCase();
  if (upper.includes('OWN_PROFILE')) {
    return {
      label: 'Oficjalny profil podmiotu (80–100%)',
      badgeClass: 'bg-emerald-50 text-[#15803D] border-emerald-200',
    };
  }
  if (upper.includes('TRANSACTIONAL') || upper.includes('P2P')) {
    return {
      label: 'Kontekst transakcyjny / P2P (60–79%)',
      badgeClass: 'bg-sky-50 text-[#0F2942] border-sky-200',
    };
  }
  if (upper.includes('FORUM')) {
    return {
      label: 'Dyskusja na forum (40–59%)',
      badgeClass: 'bg-amber-50 text-[#B45309] border-amber-200',
    };
  }
  if (upper.includes('NOISE') || upper.includes('GIVEAWAY') || upper.includes('MASS_COMMENT')) {
    return {
      label: 'Szum / Komentarze konkursowe (10–39%)',
      badgeClass: 'bg-red-50 text-[#B91C1C] border-red-200',
    };
  }
  return {
    label: context || 'Źródło publiczne OSINT',
    badgeClass: 'bg-slate-100 text-[#334155] border-slate-200',
  };
}

export function translateDirection(direction: string | null | undefined): {
  label: string;
  isOutflow: boolean;
} {
  const upper = (direction ?? '').toUpperCase();
  if (upper.includes('OUT') || upper === 'SEND' || upper.includes('WYCH')) {
    return { label: 'Wychodząca', isOutflow: true };
  }
  if (upper.includes('IN') || upper === 'RECEIVE' || upper.includes('PRZYCH')) {
    return { label: 'Przychodząca', isOutflow: false };
  }
  return { label: direction || 'Transfer', isOutflow: false };
}

export function translateScoreChangeType(changeType: string | null | undefined): {
  label: string;
  badgeClass: string;
} {
  switch ((changeType ?? '').toUpperCase()) {
    case 'ROTATED':
      return {
        label: 'Podmiana portfela',
        badgeClass: 'bg-red-100 text-[#B91C1C] border-red-300',
      };
    case 'ADDED':
      return {
        label: 'Nowo dodany adres',
        badgeClass: 'bg-emerald-50 text-[#15803D] border-emerald-200',
      };
    case 'REMOVED':
      return {
        label: 'Usunięty z profilu',
        badgeClass: 'bg-rose-50 text-[#B91C1C] border-rose-200',
      };
    case 'INCREASED':
      return {
        label: 'Wzrost pewności',
        badgeClass: 'bg-emerald-50 text-[#15803D] border-emerald-200',
      };
    case 'DECREASED':
      return {
        label: 'Spadek pewności',
        badgeClass: 'bg-amber-50 text-[#B45309] border-amber-200',
      };
    case 'UNCHANGED':
      return {
        label: 'Bez zmian',
        badgeClass: 'bg-slate-100 text-[#334155] border-slate-200',
      };
    default:
      return {
        label: changeType || 'Zmiana statusu',
        badgeClass: 'bg-slate-100 text-[#334155] border-slate-200',
      };
  }
}

export function translateMetadataKey(key: string): string {
  const map: Record<string, string> = {
    sourceName: 'Źródło OSINT',
    sourceCategory: 'Kategoria źródła',
    url: 'Adres URL źródła',
    fetchedAt: 'Znacznik czasu UTC',
    timestampUtc: 'Znacznik czasu UTC',
    sha256: 'Skrót kryptograficzny SHA-256',
    snippet: 'Cytat dowodowy',
    quoteSnippet: 'Cytat dowodowy',
    matchedIdentifier: 'Dopasowany identyfikator',
    contextClassification: 'Klasyfikacja kontekstu',
    address: 'Adres kryptowalutowy',
    network: 'Sieć blockchain',
    checksumFormat: 'Format sumy kontrolnej',
    confidenceScore: 'Wskaźnik pewności (%)',
    confidenceLevelPl: 'Poziom pewności',
    rejectionCategory: 'Kategoria wykluczenia FP',
    rejectionReason: 'Powód odrzucenia FP',
    entityTag: 'Tag podmiotu / kontraktu',
    signalType: 'Typ sygnału Cash-Out',
    severity: 'Priorytet weryfikacji',
    estimatedPln: 'Szacowana wartość (PLN)',
    counterpartyName: 'Strona przeciwna / CASP',
    verificationHintPl: 'Zalecenie weryfikacyjne KAS',
    identifierType: 'Typ identyfikatora',
    identifierValue: 'Wartość identyfikatora',
    txCount: 'Liczba transakcji',
    estimatedTotalVolumePln: 'Łączny wolumen (PLN)',
    stablecoinVolumeUsd: 'Wolumen Stablecoin (USD)',
  };
  return map[key] ?? key;
}

export function translateAiProvider(provider: string | null | undefined): string {
  if (!provider) return 'Lokalny silnik reguł (On-Premise)';
  switch (provider.toLowerCase()) {
    case 'local-rules':
      return 'Lokalny silnik reguł (On-Premise)';
    case 'ollama':
      return 'Lokalny serwer Ollama (On-Premise)';
    case 'openai-compatible':
      return 'Lokalny model OpenAI-compatible (On-Premise)';
    default:
      return `${provider} (On-Premise)`;
  }
}

export function getExplorerUrl(network: string, addressOrTx: string, kind: 'address' | 'tx'): string | null {
  const net = network.toUpperCase();
  if (net === 'ETH' || net === 'EVM') {
    return kind === 'tx'
      ? `https://etherscan.io/tx/${addressOrTx}`
      : `https://etherscan.io/address/${addressOrTx}`;
  }
  if (net === 'BTC') {
    return kind === 'tx'
      ? `https://mempool.space/tx/${addressOrTx}`
      : `https://mempool.space/address/${addressOrTx}`;
  }
  if (net === 'TRON' || net === 'TRX') {
    return kind === 'tx'
      ? `https://tronscan.org/#/transaction/${addressOrTx}`
      : `https://tronscan.org/#/address/${addressOrTx}`;
  }
  return null;
}

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
