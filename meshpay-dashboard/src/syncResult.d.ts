export type PacketSyncState = 'SYNCHRONIZED' | 'DUPLICATE' | 'IN_PROGRESS' | 'RETRY_PENDING' | 'REJECTED' | 'FAILED';
export function syncResult(
  httpOk: boolean,
  httpStatus: number,
  data?: { status?: string; message?: string },
): { state: PacketSyncState; message: string };
