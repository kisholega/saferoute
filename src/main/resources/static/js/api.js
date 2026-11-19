// Talks to the SafeRoute backend. Every failure becomes an ApiError with a message that is safe to show.

export class ApiError extends Error {
    constructor(message, status = 0, fieldErrors = {}) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.fieldErrors = fieldErrors;
    }
}

export async function api(path, { method = 'GET', body, signal, quiet401 = false } = {}) {
    let response;
    try {
        response = await fetch(path, {
            method,
            signal,
            credentials: 'same-origin',
            headers: body === undefined ? { Accept: 'application/json' } : { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch (error) {
        if (error.name === 'AbortError') throw error;
        throw new ApiError('Cannot reach the SafeRoute server. Check that it is running and that you are online.');
    }

    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        if (response.status === 401 && !quiet401) {
            window.dispatchEvent(new CustomEvent('auth:expired'));
        }
        throw new ApiError(data?.message || `Request failed (${response.status}).`, response.status, data?.fieldErrors || {});
    }
    return data;
}

export const get = (path, options) => api(path, options);
export const post = (path, body, options) => api(path, { ...options, method: 'POST', body: body ?? {} });
export const put = (path, body) => api(path, { method: 'PUT', body });
export const del = path => api(path, { method: 'DELETE' });
