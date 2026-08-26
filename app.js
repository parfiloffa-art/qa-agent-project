import {
  calculateAnnuity,
  calculateDifferentiated,
  formatAmountInput,
  formatMoney,
  formatRate,
  formatTerm,
  parseAmount,
  parseNonNegativeInteger,
  parseRate,
  validateTermMonths,
} from './calculator.js';

const form = document.querySelector('#loan-form');
const amountInput = document.querySelector('#amount');
const rateInput = document.querySelector('#rate');
const monthsInput = document.querySelector('#term-months');
const yearsInput = document.querySelector('#term-years');
const remainderInput = document.querySelector('#term-remainder');
const termMonthsGroup = document.querySelector('#term-months-group');
const termYearsGroup = document.querySelector('#term-years-group');
const resetButton = document.querySelector('#reset-button');
const emptyResult = document.querySelector('#empty-result');
const results = document.querySelector('#results');
const schedule = document.querySelector('#schedule');
const scheduleToggle = document.querySelector('#schedule-toggle');

const trackedInputs = {
  amount: amountInput,
  rate: rateInput,
  months: monthsInput,
  years: yearsInput,
  remainder: remainderInput,
};

const state = {
  termMode: 'months',
  paymentType: 'annuity',
  scheduleExpanded: false,
  changed: false,
  touched: createFieldFlags(),
  hadContent: createFieldFlags(),
};

function createFieldFlags() {
  return {
    amount: false,
    rate: false,
    months: false,
    years: false,
    remainder: false,
  };
}

function shouldShowRequired(fieldName) {
  return state.touched[fieldName] || state.hadContent[fieldName];
}

function setError(inputNames, errorElementId, message) {
  const names = Array.isArray(inputNames) ? inputNames : [inputNames];
  const errorElement = document.querySelector(`#${errorElementId}`);
  const field = errorElement.closest('.field');

  names.forEach((name) => {
    trackedInputs[name].setAttribute('aria-invalid', message ? 'true' : 'false');
  });
  errorElement.textContent = message;
  errorElement.hidden = !message;
  field.classList.toggle('field--error', Boolean(message));
}

function amountError(parsed) {
  if (parsed.status === 'empty') {
    return shouldShowRequired('amount') ? 'Заполните поле.' : '';
  }
  if (parsed.status === 'format') {
    return 'Введите целую сумму в рублях, например 100 000.';
  }
  if (parsed.status === 'range') {
    if (parsed.value > 30_000_000) return 'Слишком смело. Максимум — 30 000 000 ₽.';
    return 'Сумма должна быть от 10 000 до 30 000 000 ₽.';
  }
  return '';
}

function rateError(parsed) {
  if (parsed.status === 'empty') {
    return shouldShowRequired('rate') ? 'Заполните поле.' : '';
  }
  if (parsed.status === 'format') {
    return 'Введите ставку числом с точкой или запятой и максимум двумя знаками после неё.';
  }
  if (parsed.status === 'range') {
    return 'Ставка должна быть от 0,01% до 133%.';
  }
  return '';
}

function readTerm() {
  if (state.termMode === 'months') {
    const parsed = parseNonNegativeInteger(monthsInput.value);
    let message = '';

    if (parsed.status === 'empty') {
      message = shouldShowRequired('months') ? 'Заполните поле.' : '';
      return { status: 'empty', message };
    }
    if (parsed.status === 'format') {
      return { status: 'format', message: 'Введите целое количество месяцев без знаков и дробной части.' };
    }

    const checked = validateTermMonths(parsed.value);
    if (checked.status === 'range') {
      return { status: 'range', value: parsed.value, message: 'Срок должен быть от 1 до 360 месяцев.' };
    }
    return { status: 'valid', value: parsed.value, message: '' };
  }

  const years = parseNonNegativeInteger(yearsInput.value);
  const remainder = parseNonNegativeInteger(remainderInput.value);

  if (years.status === 'format' || remainder.status === 'format') {
    return {
      status: 'format',
      message: 'Введите целые неотрицательные значения без знаков и дробной части.',
    };
  }

  if (years.status === 'empty' || remainder.status === 'empty') {
    const missingFieldIsTouched =
      (years.status === 'empty' && shouldShowRequired('years')) ||
      (remainder.status === 'empty' && shouldShowRequired('remainder'));
    return { status: 'empty', message: missingFieldIsTouched ? 'Заполните оба поля срока.' : '' };
  }

  if (remainder.value > 11) {
    return { status: 'range', message: 'Остаток месяцев должен быть от 0 до 11.' };
  }

  const totalMonths = years.value * 12 + remainder.value;
  if (!Number.isSafeInteger(totalMonths)) {
    return { status: 'format', message: 'Срок задан слишком большим числом.' };
  }

  const checked = validateTermMonths(totalMonths);
  if (checked.status === 'range') {
    return { status: 'range', value: totalMonths, message: 'Общий срок должен быть от 1 до 360 месяцев.' };
  }
  return { status: 'valid', value: totalMonths, message: '' };
}

function readForm() {
  const amount = parseAmount(amountInput.value);
  const rate = parseRate(rateInput.value);
  const term = readTerm();

  setError('amount', 'amount-error', amountError(amount));
  setError('rate', 'rate-error', rateError(rate));
  setError(['months', 'years', 'remainder'], 'term-error', term.message);

  if (amount.status !== 'valid' || rate.status !== 'valid' || term.status !== 'valid') {
    return null;
  }

  return {
    amount: amount.value,
    rate: rate.value,
    months: term.value,
  };
}

function metric(label, value, featured = false) {
  return `
    <div class="metric${featured ? ' metric--featured' : ''}">
      <dt>${label}</dt>
      <dd>${value}</dd>
    </div>
  `;
}

function renderSelectedResult(data, annuity, differentiated) {
  const title = document.querySelector('#selected-scheme-title');
  const badge = document.querySelector('#selected-scheme-badge');
  const metrics = document.querySelector('#result-metrics');

  document.querySelector('#summary-amount').textContent = formatMoney(data.amount * 100);
  document.querySelector('#summary-rate').textContent = formatRate(data.rate);
  document.querySelector('#summary-term').textContent = formatTerm(data.months);

  if (state.paymentType === 'annuity') {
    title.textContent = 'Аннуитетные платежи';
    badge.textContent = 'Ровный ритм';
    metrics.innerHTML = [
      metric('Ежемесячный платёж', formatMoney(annuity.monthlyPayment), true),
      metric('Общая сумма выплат', formatMoney(annuity.totalPayment)),
      metric('Переплата', formatMoney(annuity.overpayment)),
    ].join('');
    scheduleToggle.hidden = true;
  } else {
    title.textContent = 'Дифференцированные платежи';
    badge.textContent = 'На уменьшение';
    metrics.innerHTML = [
      metric('Первый платёж', formatMoney(differentiated.firstPayment), true),
      metric('Последний платёж', formatMoney(differentiated.lastPayment)),
      metric('Общая сумма выплат', formatMoney(differentiated.totalPayment)),
      metric('Переплата', formatMoney(differentiated.overpayment)),
    ].join('');
    scheduleToggle.hidden = false;
  }
}

function renderComparison(annuity, differentiated) {
  document.querySelector('#comparison-annuity-character').innerHTML =
    `<span>Один ежемесячный платёж</span><strong>${formatMoney(annuity.monthlyPayment)}</strong>`;
  document.querySelector('#comparison-diff-character').innerHTML =
    `<span>Первый → последний</span><strong>${formatMoney(differentiated.firstPayment)} → ${formatMoney(differentiated.lastPayment)}</strong>`;
  document.querySelector('#comparison-annuity-total').textContent = formatMoney(annuity.totalPayment);
  document.querySelector('#comparison-diff-total').textContent = formatMoney(differentiated.totalPayment);
  document.querySelector('#comparison-annuity-overpayment').textContent = formatMoney(annuity.overpayment);
  document.querySelector('#comparison-diff-overpayment').textContent = formatMoney(differentiated.overpayment);

  const difference = Math.abs(annuity.overpayment - differentiated.overpayment);
  let conclusion = 'Расчётная переплата одинакова у обеих схем.';
  if (annuity.overpayment < differentiated.overpayment) {
    conclusion = 'Расчётная переплата меньше у аннуитетной схемы.';
  } else if (differentiated.overpayment < annuity.overpayment) {
    conclusion = 'Расчётная переплата меньше у дифференцированной схемы.';
  }

  document.querySelector('#difference-text').innerHTML = `
    <span>Разница в переплате</span>
    <strong>${formatMoney(difference)}</strong>
    <p>${conclusion} Это сравнение, а не финансовая рекомендация.</p>
  `;
}

function renderSchedule(differentiated) {
  const body = document.querySelector('#schedule-body');
  const fragment = document.createDocumentFragment();

  differentiated.schedule.forEach((row) => {
    const tableRow = document.createElement('tr');
    [
      row.month,
      formatMoney(row.payment),
      formatMoney(row.principal),
      formatMoney(row.interest),
      formatMoney(row.balance),
    ].forEach((value, index) => {
      const cell = document.createElement(index === 0 ? 'th' : 'td');
      if (index === 0) cell.scope = 'row';
      cell.textContent = value;
      tableRow.append(cell);
    });
    fragment.append(tableRow);
  });

  body.replaceChildren(fragment);
  document.querySelector('#schedule-count').textContent = formatTerm(differentiated.schedule.length);
}

function hideResults() {
  results.hidden = true;
  schedule.hidden = true;
  emptyResult.hidden = false;
}

function render() {
  resetButton.hidden = !state.changed;
  const data = readForm();

  if (!data) {
    hideResults();
    return;
  }

  const annuity = calculateAnnuity(data.amount, data.rate, data.months);
  const differentiated = calculateDifferentiated(data.amount, data.rate, data.months);

  renderSelectedResult(data, annuity, differentiated);
  renderComparison(annuity, differentiated);
  renderSchedule(differentiated);

  emptyResult.hidden = true;
  results.hidden = false;
  const scheduleIsVisible = state.paymentType === 'differentiated' && state.scheduleExpanded;
  schedule.hidden = !scheduleIsVisible;
  scheduleToggle.setAttribute('aria-expanded', String(scheduleIsVisible));
  scheduleToggle.innerHTML = scheduleIsVisible
    ? 'Скрыть график <span aria-hidden="true">↑</span>'
    : 'Показать график <span aria-hidden="true">↓</span>';
}

function markInput(fieldName) {
  state.changed = true;
  if (trackedInputs[fieldName].value.trim()) state.hadContent[fieldName] = true;
  render();
}

function normalizeOnBlur(fieldName) {
  state.touched[fieldName] = true;
  const input = trackedInputs[fieldName];
  input.value = input.value.trim();

  if (fieldName === 'amount') {
    const parsed = parseAmount(input.value);
    if (parsed.status === 'valid' || parsed.status === 'range') {
      input.value = formatAmountInput(parsed.value);
    }
  }
  render();
}

function setConvertedInteraction(sourceNames, targetNames) {
  const wasTouched = sourceNames.some((name) => state.touched[name]);
  targetNames.forEach((name) => {
    state.touched[name] = wasTouched;
    state.hadContent[name] = Boolean(trackedInputs[name].value.trim());
  });
}

function switchTermMode(nextMode) {
  if (nextMode === state.termMode) return;

  if (nextMode === 'years') {
    const parsed = parseNonNegativeInteger(monthsInput.value);
    if (parsed.status === 'valid') {
      yearsInput.value = String(Math.floor(parsed.value / 12));
      remainderInput.value = String(parsed.value % 12);
    } else {
      yearsInput.value = '';
      remainderInput.value = '';
    }
    setConvertedInteraction(['months'], ['years', 'remainder']);
  } else {
    const years = parseNonNegativeInteger(yearsInput.value);
    const remainder = parseNonNegativeInteger(remainderInput.value);
    if (years.status === 'valid' && remainder.status === 'valid') {
      const total = years.value * 12 + remainder.value;
      monthsInput.value = Number.isSafeInteger(total) ? String(total) : '';
    } else {
      monthsInput.value = '';
    }
    setConvertedInteraction(['years', 'remainder'], ['months']);
  }

  state.termMode = nextMode;
  state.changed = true;
  termMonthsGroup.hidden = nextMode !== 'months';
  termYearsGroup.hidden = nextMode !== 'years';
  render();
}

Object.entries(trackedInputs).forEach(([fieldName, input]) => {
  input.addEventListener('input', () => markInput(fieldName));
  input.addEventListener('blur', () => normalizeOnBlur(fieldName));
});

document.querySelectorAll('input[name="term-unit"]').forEach((radio) => {
  radio.addEventListener('change', (event) => switchTermMode(event.target.value));
});

document.querySelectorAll('input[name="payment-type"]').forEach((radio) => {
  radio.addEventListener('change', (event) => {
    state.paymentType = event.target.value;
    state.changed = true;
    if (state.paymentType === 'annuity') state.scheduleExpanded = false;
    render();
  });
});

scheduleToggle.addEventListener('click', () => {
  state.scheduleExpanded = !state.scheduleExpanded;
  render();
  if (state.scheduleExpanded) schedule.scrollIntoView({ behavior: 'smooth', block: 'start' });
});

form.addEventListener('submit', (event) => event.preventDefault());

form.addEventListener('reset', () => {
  window.requestAnimationFrame(() => {
    state.termMode = 'months';
    state.paymentType = 'annuity';
    state.scheduleExpanded = false;
    state.changed = false;
    state.touched = createFieldFlags();
    state.hadContent = createFieldFlags();
    termMonthsGroup.hidden = false;
    termYearsGroup.hidden = true;
    setError('amount', 'amount-error', '');
    setError('rate', 'rate-error', '');
    setError(['months', 'years', 'remainder'], 'term-error', '');
    render();
  });
});

render();
