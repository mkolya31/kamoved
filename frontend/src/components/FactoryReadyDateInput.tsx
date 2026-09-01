import { useRef, type ClipboardEvent, type KeyboardEvent } from 'react'
import {
  emptyFactoryReadyDate,
  isEmptyFactoryReadyDate,
  maskFactoryReadyDate,
} from '../lib/factoryReadyDate'

interface FactoryReadyDateInputProps {
  value: string
  onChange: (value: string) => void
  autoFocus?: boolean
  ariaLabel?: string
  ariaInvalid?: boolean
  ariaDescribedBy?: string
}

const digitPositions = [0, 1, 3, 4, 6, 7, 8, 9]

export function FactoryReadyDateInput({
  value,
  onChange,
  autoFocus,
  ariaLabel = 'Дата в формате ДД.ММ.ГГГГ',
  ariaInvalid,
  ariaDescribedBy,
}: FactoryReadyDateInputProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  function moveCursor(position: number) {
    requestAnimationFrame(() => inputRef.current?.setSelectionRange(position, position))
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.ctrlKey || event.metaKey || event.altKey) return
    const input = event.currentTarget
    const start = input.selectionStart ?? 0
    const end = input.selectionEnd ?? start
    const characters = [...(value || emptyFactoryReadyDate())]

    if (/^\d$/.test(event.key)) {
      event.preventDefault()
      if (end > start) {
        digitPositions.filter((position) => position >= start && position < end)
          .forEach((position) => { characters[position] = '_' })
      }
      const position = digitPositions.find((candidate) => candidate >= start)
      if (position === undefined) return
      characters[position] = event.key
      onChange(characters.join(''))
      moveCursor(digitPositions.find((candidate) => candidate > position) ?? 10)
      return
    }

    if (event.key === 'Backspace' || event.key === 'Delete') {
      event.preventDefault()
      if (end > start) {
        if (start === 0 && end >= 10) {
          onChange(emptyFactoryReadyDate())
          moveCursor(0)
          return
        }
        digitPositions.filter((position) => position >= start && position < end)
          .forEach((position) => { characters[position] = '_' })
        onChange(characters.join(''))
        moveCursor(start)
        return
      }
      const candidates = event.key === 'Backspace'
        ? [...digitPositions].reverse().filter((position) => position < start)
        : digitPositions.filter((position) => position >= start)
      const position = candidates[0]
      if (position === undefined) return
      characters[position] = '_'
      onChange(characters.join(''))
      moveCursor(position)
    }
  }

  function handlePaste(event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault()
    const pasted = maskFactoryReadyDate(event.clipboardData.getData('text'))
    if (/^\d{2}\.\d{2}\.\d{4}$/.test(pasted)) onChange(pasted)
  }

  return (
    <input
      ref={inputRef}
      type="text"
      inputMode="numeric"
      value={value}
      onKeyDown={handleKeyDown}
      onPaste={handlePaste}
      onChange={(event) => {
        if (!event.target.value) onChange(emptyFactoryReadyDate())
      }}
      onFocus={(event) => {
        if (isEmptyFactoryReadyDate(value)) event.currentTarget.setSelectionRange(0, 5)
      }}
      maxLength={10}
      aria-label={ariaLabel}
      aria-invalid={ariaInvalid}
      aria-describedby={ariaDescribedBy}
      autoFocus={autoFocus}
    />
  )
}
