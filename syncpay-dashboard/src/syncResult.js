const acceptedSyncStates = new Set(['SYNCHRONIZED', 'DUPLICATE', 'IN_PROGRESS']);

export function syncResult(httpOk, httpStatus, data = {}) {
  // Never trust success-looking status/message fields in a non-2xx response.
  if (!httpOk) {
    if (httpStatus === 401) return { state: 'REJECTED', message: 'Authentication required; packet was not accepted.' };
    if (httpStatus === 403) return { state: 'REJECTED', message: 'Not authorized; packet was not accepted.' };
    if (httpStatus >= 500 || httpStatus === 429) {
      return { state: 'RETRY_PENDING', message: data.message || `HTTP ${httpStatus}; packet was not accepted.` };
    }
    return { state: 'REJECTED', message: data.message || `HTTP ${httpStatus}; packet was not accepted.` };
  }

  if (acceptedSyncStates.has(data.status)) {
    return { state: data.status, message: data.message || 'Packet synchronized' };
  }
  return { state: 'FAILED', message: 'Unexpected server response; packet was not marked accepted.' };
}
