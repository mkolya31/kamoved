import { describe, expect, it } from 'vitest'
import { formatPhone } from './phone'

describe('formatPhone', () => {
  describe('пустой ввод', () => {
    it('возвращает пустую строку, если цифр нет', () => {
      expect(formatPhone('')).toBe('')
      expect(formatPhone('   ')).toBe('')
      expect(formatPhone('abc')).toBe('')
    })
  })

  describe('первый символ — код страны', () => {
    it('8 превращается в заготовку +7 (', () => {
      expect(formatPhone('8')).toBe('+7 (')
    })

    it('7 превращается в заготовку +7 (', () => {
      expect(formatPhone('7')).toBe('+7 (')
    })

    it('+ превращается в заготовку +7 (', () => {
      expect(formatPhone('+')).toBe('+7 (')
    })

    it('символ кода страны не попадает в номер', () => {
      expect(formatPhone('89')).toBe('+7 (9')
      expect(formatPhone('79')).toBe('+7 (9')
      expect(formatPhone('+79')).toBe('+7 (9')
    })
  })

  describe('первый символ — цифра номера', () => {
    it('становится первой цифрой номера', () => {
      expect(formatPhone('9')).toBe('+7 (9')
    })

    it('следующие цифры заполняют маску', () => {
      expect(formatPhone('91')).toBe('+7 (91')
      expect(formatPhone('915')).toBe('+7 (915')
      expect(formatPhone('9151')).toBe('+7 (915) 1')
      expect(formatPhone('91519494')).toBe('+7 (915) 194-94')
      expect(formatPhone('9151949419')).toBe('+7 (915) 194-94-19')
    })
  })

  describe('вставка номера целиком', () => {
    it.each([
      ['89151949419'],
      ['+79151949419'],
      ['79151949419'],
      ['8 915 194-94-19'],
      ['9151949419'],
      ['+7 (915) 194-94-19'],
    ])('приводит %s к маске', (raw) => {
      expect(formatPhone(raw)).toBe('+7 (915) 194-94-19')
    })
  })

  describe('лишние символы', () => {
    it('игнорирует нецифровые символы', () => {
      expect(formatPhone('9a1b5c')).toBe('+7 (915')
    })

    it('отбрасывает цифры сверх 10 цифр номера', () => {
      expect(formatPhone('891519494199999')).toBe('+7 (915) 194-94-19')
      expect(formatPhone('91519494190000')).toBe('+7 (915) 194-94-19')
    })
  })

  describe('удаление (Backspace)', () => {
    it('удаляет последнюю цифру номера', () => {
      // значение '+7 (915) 194-94-19' → Backspace удаляет '9'
      expect(formatPhone('+7 (915) 194-94-1')).toBe('+7 (915) 194-94-1')
      // значение '+7 (915) 194-94-1' → Backspace удаляет '1'
      expect(formatPhone('+7 (915) 194-94-')).toBe('+7 (915) 194-94')
    })

    it('убирает разделитель, если цифра рядом с ним удалена', () => {
      // значение '+7 (915) 1' → Backspace удаляет '1', остаётся '+7 (915) '
      expect(formatPhone('+7 (915) ')).toBe('+7 (915')
    })

    it('сохраняет заготовку, когда удалена последняя цифра номера', () => {
      // значение '+7 (9' → Backspace удаляет '9', остаётся '+7 ('
      expect(formatPhone('+7 (')).toBe('+7 (')
    })

    it('очищает поле при Backspace на заготовке +7 (', () => {
      // значение '+7 (' → Backspace удаляет '(', остаётся '+7 '
      expect(formatPhone('+7 ')).toBe('')
    })
  })

  describe('идемпотентность', () => {
    it('не меняет уже отформатированное значение', () => {
      expect(formatPhone('+7 (915) 194-94-19')).toBe('+7 (915) 194-94-19')
      expect(formatPhone('+7 (')).toBe('+7 (')
      expect(formatPhone('+7 (915')).toBe('+7 (915')
      expect(formatPhone('+7 (915) 194')).toBe('+7 (915) 194')
    })
  })
})
