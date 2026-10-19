import { readResponse } from './http';

export interface MappingRule {
  source: string | null; target: string; transform: 'COPY'|'STRING'|'NUMBER'|'BOOLEAN'|'DATE';
  defaultValue?: unknown; required: boolean; nullPolicy: 'KEEP'|'OMIT'|'ERROR'|'DEFAULT';
  sourceFormat?: string|null; targetFormat?: string|null;
}
export interface ValidationRule {
  field: string; stage: 'SOURCE'|'TARGET'; type: 'STRING'|'NUMBER'|'BOOLEAN'|'OBJECT'|'ARRAY';
  required: boolean; nullable: boolean; minLength?: number|null; maxLength?: number|null;
  minValue?: number|null; maxValue?: number|null; enumValues?: unknown[]|null;
  dateFormat?: string|null; pattern?: string|null;
}
export interface Definition {
  interfaceId: string; interfaceName: string; description: string;
  sourceSystem: string; targetSystem: string; method: 'POST'; path: string; enabled: boolean;
  timeoutMs: number; retryMaxAttempts: number; version: number;
  mappings: MappingRule[]; validations: ValidationRule[];
}
export interface SystemInfo { systemId: string; systemName: string; role: 'SOURCE'|'TARGET' }
export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...options, headers: { 'Content-Type':'application/json', ...options?.headers } });
  const data = await readResponse(response);
  if (!response.ok) {
    const error = data as {message?: string; code?: string};
    throw new Error(`${error.message ?? error.code ?? '요청 오류'} · HTTP ${response.status}`);
  }
  return data as T;
}
export function message(error: unknown): string { return error instanceof Error ? error.message : '처리 중 오류가 발생했습니다.'; }
export const pretty = (value: unknown) => JSON.stringify(value, null, 2);
