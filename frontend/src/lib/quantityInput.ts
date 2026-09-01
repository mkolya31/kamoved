export function selectDefaultQuantity(
  input: Pick<HTMLInputElement, 'value' | 'select'>,
  enabled = true,
): void {
  if (enabled && input.value === '1') input.select()
}
