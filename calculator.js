const AMOUNT_MIN = 10_000;
const AMOUNT_MAX = 30_000_000;
const RATE_MIN_HUNDREDTHS = 1;
const RATE_MAX_HUNDREDTHS = 13_300;
const TERM_MIN = 1;
const TERM_MAX = 360;

const moneyFormatter = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const rateFormatter = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const integerFormatter = new Intl.NumberFormat('ru-RU', {
  maximumFractionDigits: 0,
  useGrouping: true,
});

function emptyResult() {
  return { status: 'empty' };
}

function formatResult() {
  return { status: 'format' };
}

function rangeResult(value) {
  return { status: 'range', value };
}

function validResult(value, extra = {}) {
  return { status: 'valid', value, ...extra };
}

function roundPositive(value) {
  const tolerance = Number.EPSILON * Math.max(1, Math.abs(value)) * 4;
  return Math.floor(value + 0.5 + tolerance);
}

function roundRatioPositive(numerator, denominator) {
  const top = BigInt(numerator);
  const bottom = BigInt(denominator);
  return Number((top * 2n + bottom) / (bottom * 2n));
}

function pluralize(value, one, few, many) {
  const lastTwo = value % 100;
  const last = value % 10;

  if (lastTwo >= 11 && lastTwo <= 14) return many;
  if (last === 1) return one;
  if (last >= 2 && last <= 4) return few;
  return many;
}

export function parseAmount(rawValue) {
  const value = String(rawValue).trim();
  if (!value) return emptyResult();

  const plainDigits = /^\d+$/;
  const groupedDigits = /^\d{1,3}(?:[ \u00a0]\d{3})+$/;
  if (!plainDigits.test(value) && !groupedDigits.test(value)) {
    return formatResult();
  }

  const amount = Number(value.replace(/[ \u00a0]/g, ''));
  if (!Number.isSafeInteger(amount)) return formatResult();
  if (amount < AMOUNT_MIN || amount > AMOUNT_MAX) return rangeResult(amount);
  return validResult(amount);
}

export function parseRate(rawValue) {
  const value = String(rawValue).trim();
  if (!value) return emptyResult();
  if (!/^\d+(?:[.,]\d{1,2})?$/.test(value)) return formatResult();

  const [wholePart, fractionPart = ''] = value.replace(',', '.').split('.');
  const hundredths = Number(wholePart) * 100 + Number(fractionPart.padEnd(2, '0'));

  if (!Number.isSafeInteger(hundredths)) return formatResult();
  if (hundredths < RATE_MIN_HUNDREDTHS || hundredths > RATE_MAX_HUNDREDTHS) {
    return rangeResult(hundredths / 100);
  }

  return validResult(hundredths / 100, { hundredths });
}

export function parseNonNegativeInteger(rawValue) {
  const value = String(rawValue).trim();
  if (!value) return emptyResult();
  if (!/^\d+$/.test(value)) return formatResult();

  const integer = Number(value);
  if (!Number.isSafeInteger(integer)) return formatResult();
  return validResult(integer);
}

export function validateTermMonths(months) {
  if (!Number.isSafeInteger(months) || months < 0) return formatResult();
  if (months < TERM_MIN || months > TERM_MAX) return rangeResult(months);
  return validResult(months);
}

export function calculateAnnuity(amountRubles, annualRate, months) {
  const principal = amountRubles * 100;
  const monthlyRate = annualRate / 12 / 100;
  const growth = Math.pow(1 + monthlyRate, months);
  const rawPayment = principal * monthlyRate * growth / (growth - 1);
  const monthlyPayment = roundPositive(rawPayment);
  const totalPayment = monthlyPayment * months;

  return {
    monthlyPayment,
    totalPayment,
    overpayment: totalPayment - principal,
  };
}

export function calculateDifferentiated(amountRubles, annualRate, months) {
  const principal = amountRubles * 100;
  const rateHundredths = roundPositive(annualRate * 100);
  const regularPrincipalPart = roundRatioPositive(principal, months);
  const schedule = [];
  let balance = principal;
  let totalPayment = 0;

  for (let month = 1; month <= months; month += 1) {
    const openingBalance = balance;
    const principalPart = month === months ? balance : Math.min(regularPrincipalPart, balance);
    const interest = roundRatioPositive(openingBalance * rateHundredths, 120_000);
    const payment = principalPart + interest;
    balance -= principalPart;
    totalPayment += payment;

    schedule.push({
      month,
      payment,
      principal: principalPart,
      interest,
      balance,
    });
  }

  return {
    firstPayment: schedule[0].payment,
    lastPayment: schedule[schedule.length - 1].payment,
    totalPayment,
    overpayment: totalPayment - principal,
    schedule,
  };
}

export function formatMoney(kopecks) {
  return moneyFormatter.format(kopecks / 100);
}

export function formatAmountInput(rubles) {
  return integerFormatter.format(rubles);
}

export function formatRate(rate) {
  return `${rateFormatter.format(rate)}%`;
}

export function formatTerm(months) {
  return `${months} ${pluralize(months, 'месяц', 'месяца', 'месяцев')}`;
}

export const LIMITS = Object.freeze({
  amountMin: AMOUNT_MIN,
  amountMax: AMOUNT_MAX,
  rateMin: RATE_MIN_HUNDREDTHS / 100,
  rateMax: RATE_MAX_HUNDREDTHS / 100,
  termMin: TERM_MIN,
  termMax: TERM_MAX,
});
