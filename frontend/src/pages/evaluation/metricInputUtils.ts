export const INPUT_COMPONENT_OPTIONS = [
  { value: 'input-number', label: '数字输入' },
  { value: 'textarea', label: '多行文本' },
  { value: 'radio', label: '单选输入' },
  { value: 'checkbox-group', label: '多选输入' },
  { value: 'select', label: '下拉框输入' },
]

const MULTIPLE_VALUE_COMPONENTS = new Set(['checkbox-group'])
const OPTION_COMPONENTS = new Set(['radio', 'checkbox-group', 'select'])

export const getInputComponentLabel = (inputComponent?: string) =>
  INPUT_COMPONENT_OPTIONS.find((item) => item.value === inputComponent)?.label || inputComponent || '—'

export const isOptionInputComponent = (inputComponent?: string) => OPTION_COMPONENTS.has(inputComponent || '')

export const isMultipleOptionInputComponent = (inputComponent?: string) => MULTIPLE_VALUE_COMPONENTS.has(inputComponent || '')

export const isNumericInputComponent = (inputComponent?: string) => inputComponent === 'input-number'

export const parseMetricOptions = (optionValues?: string) =>
  (optionValues || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separatorIndex = line.indexOf('|')
      if (separatorIndex < 0) {
        return { label: line, value: line }
      }
      const value = line.slice(0, separatorIndex).trim()
      const label = line.slice(separatorIndex + 1).trim()
      return {
        value: value || label,
        label: label || value,
      }
    })
    .filter((item) => item.value && item.label)

export const parseStoredOptionValue = (inputComponent?: string, optionValue?: string): string | string[] | undefined => {
  if (!optionValue) {
    return isMultipleOptionInputComponent(inputComponent) ? [] : undefined
  }
  if (!isMultipleOptionInputComponent(inputComponent)) {
    return optionValue
  }
  try {
    const parsed = JSON.parse(optionValue)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item))
    }
  } catch {
    return optionValue.split(',').map((item) => item.trim()).filter(Boolean)
  }
  return []
}

export const stringifyStoredOptionValue = (inputComponent?: string, value?: string | string[]): string | undefined => {
  if (Array.isArray(value)) {
    const normalized = value.map((item) => item.trim()).filter(Boolean)
    return normalized.length > 0 ? JSON.stringify(normalized) : undefined
  }
  if (value == null) {
    return undefined
  }
  const normalized = value.trim()
  return normalized || undefined
}

export const formatStoredOptionValue = (inputComponent?: string, optionValue?: string, optionValues?: string): string => {
  const parsed = parseStoredOptionValue(inputComponent, optionValue)
  const labelMap = new Map(parseMetricOptions(optionValues).map((item) => [item.value, item.label]))
  if (Array.isArray(parsed)) {
    return parsed.length > 0 ? parsed.map((item) => labelMap.get(item) || item).join('、') : '—'
  }
  return parsed ? labelMap.get(parsed) || parsed : '—'
}
