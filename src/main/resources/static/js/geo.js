// Client-side geometry for live trips: distances, projecting a position onto the route, moving along it.

const R = 6371000;
const rad = d => d * Math.PI / 180;

export function distance(a, b) {
    const dLat = rad(b[0] - a[0]);
    const dLon = rad(b[1] - a[1]);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a[0])) * Math.cos(rad(b[0])) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** A route with cumulative distances, for fast "where along the route am I?" questions. */
export class Route {
    constructor(points) {
        this.points = points;
        this.cum = [0];
        for (let i = 1; i < points.length; i++) {
            this.cum.push(this.cum[i - 1] + distance(points[i - 1], points[i]));
        }
        this.length = this.cum[this.cum.length - 1];
    }

    /** The point the given number of metres along the route. */
    pointAt(meters) {
        if (meters <= 0) return this.points[0];
        if (meters >= this.length) return this.points[this.points.length - 1];
        let i = 1;
        while (this.cum[i] < meters) i++;
        const seg = this.cum[i] - this.cum[i - 1];
        const t = seg === 0 ? 0 : (meters - this.cum[i - 1]) / seg;
        const a = this.points[i - 1];
        const b = this.points[i];
        return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
    }

    /** Direction of travel (radians, local flat projection) at a distance along the route. */
    headingAt(meters) {
        const a = this.pointAt(Math.max(0, meters - 5));
        const b = this.pointAt(Math.min(this.length, meters + 5));
        return Math.atan2((b[0] - a[0]), (b[1] - a[1]) * Math.cos(rad(a[0])));
    }

    /** { off: metres from the route, along: metres along it } */
    project(p) {
        const mLat = rad(1) * R;
        const mLon = mLat * Math.cos(rad(p[0]));
        let best = { off: Infinity, along: 0 };
        for (let i = 1; i < this.points.length; i++) {
            const a = this.points[i - 1];
            const b = this.points[i];
            const ax = (a[1] - p[1]) * mLon, ay = (a[0] - p[0]) * mLat;
            const dx = (b[1] - a[1]) * mLon, dy = (b[0] - a[0]) * mLat;
            const len2 = dx * dx + dy * dy;
            const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            const off = Math.hypot(ax + t * dx, ay + t * dy);
            if (off < best.off) best = { off, along: this.cum[i - 1] + t * (this.cum[i] - this.cum[i - 1]) };
        }
        return best;
    }

    /** A point offset sideways (metres, positive = left of travel) from the route. */
    offsetPoint(meters, sideways) {
        const p = this.pointAt(meters);
        const h = this.headingAt(meters);
        const mLat = rad(1) * R;
        const mLon = mLat * Math.cos(rad(p[0]));
        return [p[0] + Math.cos(h) * sideways / mLat, p[1] - Math.sin(h) * sideways / mLon];
    }
}
