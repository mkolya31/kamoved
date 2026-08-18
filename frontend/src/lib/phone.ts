const COUNTRY_CODE_PREFIX = '+7 ('
const PHONE_NUMBER_DIGITS = 10

/**
 * Форматирует ввод телефона по маске `+7 (XXX) XXX-XX-XX`.
 * Поддерживаются только российские номера.
 *
 * Правила:
 * - первый символ `8`, `7` или `+` считается кодом страны и в номер не
 *   попадает — в поле появляется заготовка `+7 (`;
 * - любой другой первый символ становится первой цифрой номера;
 * - нецифровые символы игнорируются;
 * - цифры сверх 10 цифр номера отбрасываются;
 * - заготовка `+7 (` без цифр сохраняется, а «сломанная» заготовка
 *   (например, после Backspace на `+7 (`) очищается до пустой строки.
 *
 * Функция идемпотентна: уже отформатированное значение не меняется.
 */
export function formatPhone(input: string): string {
  if (input.trim() === '') return ''

  const digits = input.replace(/\D/g, '')
  const firstChar = input.trimStart().charAt(0)
  const startsWithCountryCode =
    firstChar === '+' || firstChar === '7' || firstChar === '8'

  let numberDigits = digits
  if (startsWithCountryCode && (digits[0] === '7' || digits[0] === '8')) {
    numberDigits = digits.slice(1)
  }
  numberDigits = numberDigits.slice(0, PHONE_NUMBER_DIGITS)

  if (numberDigits.length === 0) {
    if (input.includes('(')) return COUNTRY_CODE_PREFIX
    return /^[+78]+$/.test(input) ? COUNTRY_CODE_PREFIX : ''
  }

  const area = numberDigits.slice(0, 3)
  const city = numberDigits.slice(3, 6)
  const firstPair = numberDigits.slice(6, 8)
  const secondPair = numberDigits.slice(8, 10)

  let result = `${COUNTRY_CODE_PREFIX}${area}`
  if (city) result += `) ${city}`
  if (firstPair) result += `-${firstPair}`
  if (secondPair) result += `-${secondPair}`
  return result
}
