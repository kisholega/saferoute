// App state that survives a page refresh within the tab (sessionStorage), e.g. the trip being planned.

const KEY = 'saferoute.state.v1';
const PERSISTED = ['draft', 'results', 'selectedRouteId', 'tripContext'];

function load() {
    try {
        return JSON.parse(sessionStorage.getItem(KEY) || '{}');
    } catch {
        return {};
    }
}

export const store = {
    user: null,
    appInfo: null,
    contacts: null,
    /** The trip being planned: { from, to, time, timeMode, mode } (from/to: {label, latitude, longitude} or {label}) */
    draft: null,
    /** Last /api/routes/recommend response. */
    results: null,
    selectedRouteId: null,
    /** The route a live trip follows (segments, help points) for "risk ahead" warnings. */
    tripContext: null,
    ...load(),

    save() {
        try {
            const data = {};
            PERSISTED.forEach(key => { data[key] = this[key]; });
            sessionStorage.setItem(KEY, JSON.stringify(data));
        } catch { /* storage full or blocked */ }
    },

    reset() {
        this.user = null;
        this.contacts = null;
        this.draft = null;
        this.results = null;
        this.selectedRouteId = null;
        this.tripContext = null;
        try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
    },
};
