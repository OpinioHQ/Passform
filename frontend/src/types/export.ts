/** `POST /forms/{form_id}/export`의 입출력. SPEC 5절 239행. */
import type { Clarify, RequestContext } from './analyze';

export type ExportFormat = 'json' | 'yaml' | 'csv';

/** `form_versions`와 `context`는 함께 보낼 수 없다. */
export type ExportRequest =
  | { format: ExportFormat; form_versions?: number[]; context?: never }
  | { format: ExportFormat; context: RequestContext; form_versions?: never };

export type ExportResult = { status: 'file'; file: Blob } | { status: 'clarify'; clarify: Clarify };
