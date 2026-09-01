import { describe, expect, it, vi } from 'vitest'
import { selectDefaultQuantity } from './quantityInput'

function quantityInput(value: string) {
  return { value, select: vi.fn() }
}

describe('selectDefaultQuantity', () => {
  it('selects the default quantity', () => {
    const input = quantityInput('1')

    selectDefaultQuantity(input)

    expect(input.select).toHaveBeenCalledOnce()
  })

  it('does not select a changed quantity', () => {
    const input = quantityInput('23')

    selectDefaultQuantity(input)

    expect(input.select).not.toHaveBeenCalled()
  })

  it('does not select the quantity when the behavior is disabled', () => {
    const input = quantityInput('1')

    selectDefaultQuantity(input, false)

    expect(input.select).not.toHaveBeenCalled()
  })
})
