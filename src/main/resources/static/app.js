const map = L.map('map').setView([12.9716, 77.5946], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);

const routeLayer = L.layerGroup().addTo(map);
const zoneLayer = L.layerGroup().addTo(map);
const safeLocationLayer = L.layerGroup().addTo(map);

const defaultUserId = 1;
let currentRoutes = [];
let selectedRouteId = null;
let activeSearchToken = 0;
let startMarker = null;
let destinationMarker = null;
let selectedSafePlaceKeys = new Set();
let editingContactId = null;
const selectedLocationState = { source: null, destination: null };

function showMessage(type, text) {
    const box = document.getElementById('routeSummary');
    box.className = `alert alert-${type}`;
    box.textContent = text;
}

function showWarning(text) {
    const warningBox = document.getElementById('warningBox');
    warningBox.className = 'alert alert-warning';
    warningBox.textContent = text;
}

function getRiskClass(riskLevel) {
    if (!riskLevel) return 'bg-secondary';
    if (riskLevel.toLowerCase().includes('high')) return 'bg-danger';
    if (riskLevel.toLowerCase().includes('medium')) return 'bg-warning text-dark';
    return 'bg-success';
}

function formatMinutes(minutes) {
    if (!minutes && minutes !== 0) return '--';
    if (minutes >= 60) {
        const hours = Math.floor(minutes / 60);
        const remaining = minutes % 60;
        return remaining ? `${hours}h ${remaining}m` : `${hours}h`;
    }
    return `${minutes} min`;
}

function updateSummary(route) {
    if (!route) return;
    document.getElementById('safetyScore').textContent = route.safetyScore != null ? `${route.safetyScore}/100` : '--';
    document.getElementById('riskLevel').textContent = route.riskLevel || '--';
    document.getElementById('distanceValue').textContent = route.distanceKm != null ? `${route.distanceKm} km` : '--';
    document.getElementById('travelTimeValue').textContent = route.travelTimeMinutes != null ? formatMinutes(route.travelTimeMinutes) : '--';
    showMessage('info', `${route.label || 'Selected route'} — safety score ${route.safetyScore ?? '--'} / 100 with a ${route.riskLevel || 'unknown'} estimate.`);
    showWarning(route.warning || 'No active alert for this selected route.');
}

function clearLocationMarkers() {
    if (startMarker) {
        map.removeLayer(startMarker);
        startMarker = null;
    }
    if (destinationMarker) {
        map.removeLayer(destinationMarker);
        destinationMarker = null;
    }
}

function renderMapRoutes(routes) {
    routeLayer.clearLayers();
    currentRoutes = routes || [];
    if (!currentRoutes.length) {
        selectedRouteId = null;
        return;
    }

    if (!selectedRouteId) {
        selectedRouteId = currentRoutes[0].routeId;
    }

    const bounds = L.latLngBounds([]);
    currentRoutes.forEach((route, index) => {
        const isSelected = route.routeId === selectedRouteId;
        const routeColor = index === 0 ? '#1a73e8' : index === 1 ? '#f59e0b' : '#10b981';
        const polyline = L.polyline(route.geometry || [], {
            color: routeColor,
            weight: isSelected ? 6 : 4,
            opacity: isSelected ? 1 : 0.7,
            dashArray: isSelected ? null : '10, 8'
        }).addTo(routeLayer);
        polyline.bindPopup(`<strong>${route.label}</strong><br>Safety score: ${route.safetyScore}<br>Risk: ${route.riskLevel}`);

        (route.geometry || []).forEach(([lat, lon]) => bounds.extend([lat, lon]));
    });

    if (bounds.isValid()) {
        map.fitBounds(bounds.pad(0.25));
    }

    const selectedRoute = currentRoutes.find(route => route.routeId === selectedRouteId) || currentRoutes[0];
    updateSummary(selectedRoute);
    renderRouteCards(currentRoutes);
}

function renderRouteCards(routes) {
    const container = document.getElementById('routeCards');
    container.innerHTML = '';

    routes.forEach((route, index) => {
        const card = document.createElement('div');
        const selected = route.routeId === selectedRouteId;
        card.className = `route-card ${selected ? 'selected' : ''}`;
        card.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-2">
                <h5 class="mb-0">${route.label || `Route ${index + 1}`}</h5>
                <span class="badge ${getRiskClass(route.riskLevel)}">${route.riskLevel || 'Unknown'}</span>
            </div>
            <div class="route-metrics">
                <span>Distance: ${route.distanceKm ?? '--'} km</span>
                <span>Time: ${route.travelTimeMinutes != null ? formatMinutes(route.travelTimeMinutes) : '--'}</span>
                <span>Safety: ${route.safetyScore ?? '--'} / 100</span>
            </div>
            <div class="mt-2 small text-muted">${route.warning || 'No extra alert information available.'}</div>
            <button type="button" class="btn btn-sm mt-3 ${selected ? 'btn-primary' : 'btn-outline-primary'} select-route-btn" data-route-id="${route.routeId}">
                ${selected ? 'Selected route' : 'Select route'}
            </button>
        `;
        const selectButton = card.querySelector('.select-route-btn');
        selectButton.addEventListener('click', () => {
            selectedRouteId = route.routeId;
            renderMapRoutes(currentRoutes);
            updateSummary(route);
        });
        container.appendChild(card);
    });
}

function renderSafetyZones(zones) {
    zoneLayer.clearLayers();
    zones.forEach(zone => {
        const circle = L.circleMarker([zone.latitude, zone.longitude], {
            radius: 12,
            color: zone.riskLevel === 'High Risk' ? '#ef476f' : '#ffb703',
            fillColor: zone.riskLevel === 'High Risk' ? '#ef476f' : '#ffb703',
            fillOpacity: 0.6
        }).addTo(zoneLayer);
        circle.bindPopup(`<strong>${zone.name}</strong><br>${zone.description}<br>Risk: ${zone.riskLevel}`);
    });
}

function renderSafeLocations(locations) {
    safeLocationLayer.clearLayers();
    const listContainer = document.getElementById('safeLocationList');
    listContainer.innerHTML = '';

    if (!locations || !locations.length) {
        listContainer.innerHTML = '<div class="alert alert-light mb-0">No nearby safe locations were returned for the selected route.</div>';
        return;
    }

    locations.forEach(location => {
        const key = `${location.name}-${location.type}-${location.latitude}-${location.longitude}`;
        const marker = L.marker([location.latitude, location.longitude], {
            icon: L.divIcon({
                className: 'safe-place-marker',
                html: '<span style="background:#198754;border-radius:50%;display:block;width:14px;height:14px;border:2px solid #fff;box-shadow:0 0 0 2px rgba(25,135,84,.3);"></span>',
                iconSize: [14, 14],
                iconAnchor: [7, 7]
            })
        }).addTo(safeLocationLayer);
        marker.bindPopup(`<strong>${location.name}</strong><br>${location.type}<br>${location.description || 'Demo safe place'}`);
        marker.on('click', () => {
            map.setView([location.latitude, location.longitude], 15);
        });

        const card = document.createElement('div');
        const isSelected = selectedSafePlaceKeys.has(key);
        card.className = `safe-place-card ${isSelected ? 'selected' : ''}`;
        card.innerHTML = `
            <div class="d-flex justify-content-between align-items-center gap-2">
                <div>
                    <span class="safe-place-type">${location.type}</span>
                    <h5 class="mb-1">${location.name}</h5>
                </div>
                <input type="checkbox" ${isSelected ? 'checked' : ''} aria-label="Select ${location.name}" />
            </div>
            <p class="mb-1"><strong>Distance:</strong> ${location.distanceKm} km</p>
            <p class="mb-0 text-muted">${location.description || 'Demo location only.'}</p>
        `;
        const checkbox = card.querySelector('input');
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                selectedSafePlaceKeys.add(key);
                map.setView([location.latitude, location.longitude], 15);
            } else {
                selectedSafePlaceKeys.delete(key);
            }
            renderSafeLocations(locations);
        });
        listContainer.appendChild(card);
    });
}

async function fetchSafetyZones() {
    try {
        const response = await fetch('/api/safety-zones');
        const zones = await response.json();
        renderSafetyZones(zones || []);
    } catch (error) {
        console.error('Unable to load safety zones', error);
    }
}

async function fetchNearbySafeLocations(latitude, longitude) {
    try {
        const response = await fetch(`/api/safe-locations?latitude=${latitude}&longitude=${longitude}`);
        const locations = await response.json();
        renderSafeLocations(locations || []);
    } catch (error) {
        console.error('Unable to load nearby locations', error);
    }
}

function setMapLocationMarkers(start, destination) {
    clearLocationMarkers();
    if (start && start.latitude && start.longitude) {
        startMarker = L.marker([start.latitude, start.longitude], {
            title: 'Starting point'
        }).addTo(map);
        startMarker.bindPopup(`<strong>Start</strong><br>${start.displayName || 'Starting location'}`);
    }
    if (destination && destination.latitude && destination.longitude) {
        destinationMarker = L.marker([destination.latitude, destination.longitude], {
            title: 'Destination'
        }).addTo(map);
        destinationMarker.bindPopup(`<strong>Destination</strong><br>${destination.displayName || 'Destination'}`);
    }
    const bounds = L.latLngBounds([]);
    if (startMarker) bounds.extend(startMarker.getLatLng());
    if (destinationMarker) bounds.extend(destinationMarker.getLatLng());
    if (bounds.isValid()) {
        map.fitBounds(bounds.pad(0.25));
    }
}

function debounce(fn, delay) {
    let timeoutId;
    return (...args) => {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => fn(...args), delay);
    };
}

function renderLocationSearchResults(field, matches) {
    const resultsContainer = document.getElementById('locationSearchResults');
    resultsContainer.innerHTML = '';

    if (!matches || !matches.length) {
        return;
    }

    const list = document.createElement('div');
    list.className = 'list-group mt-2';

    matches.forEach(match => {
        const displayName = match.displayName || match.name || 'Selected location';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'list-group-item list-group-item-action';
        button.innerHTML = `<strong>${displayName}</strong><div class="small text-muted">${match.address || 'OpenStreetMap result'}</div>`;
        button.addEventListener('click', () => {
            const targetInput = document.getElementById(field === 'source' ? 'sourceInput' : 'destinationInput');
            targetInput.value = displayName;
            selectedLocationState[field] = {
                displayName,
                latitude: Number(match.latitude),
                longitude: Number(match.longitude)
            };
            resultsContainer.innerHTML = '';
        });
        list.appendChild(button);
    });

    resultsContainer.appendChild(list);
}

async function handleLocationSearch(field) {
    const input = document.getElementById(field === 'source' ? 'sourceInput' : 'destinationInput');
    const query = input.value.trim();
    if (!query || query.length < 2) {
        document.getElementById('locationSearchResults').innerHTML = '';
        return;
    }

    try {
        const response = await fetch(`/api/locations/search?query=${encodeURIComponent(query)}`);
        if (!response.ok) {
            throw new Error('Unable to fetch location suggestions.');
        }
        const matches = await response.json();
        renderLocationSearchResults(field, matches || []);
    } catch (error) {
        document.getElementById('locationSearchResults').innerHTML = '';
    }
}

async function handleRouteSubmit(event) {
    event.preventDefault();

    const source = document.getElementById('sourceInput').value.trim();
    const destination = document.getElementById('destinationInput').value.trim();
    const travelDate = document.getElementById('travelDate').value;
    const travelTime = document.getElementById('travelTimeInput').value;

    if (!source || !destination || !travelDate || !travelTime) {
        showMessage('danger', 'Enter both locations and a valid travel date and time before searching.');
        return;
    }

    const selectedDateTime = new Date(`${travelDate}T${travelTime}`);
    if (Number.isNaN(selectedDateTime.getTime())) {
        showMessage('danger', 'The selected travel date and time are invalid. Please pick a valid value.');
        return;
    }
    if (selectedDateTime < new Date()) {
        showMessage('warning', 'This route is being evaluated for a past journey. The estimate uses the historical input as provided.');
    }

    const sourceLocation = selectedLocationState.source || { displayName: source };
    const destinationLocation = selectedLocationState.destination || { displayName: destination };

    const requestId = ++activeSearchToken;
    showMessage('info', 'Searching for the safest route and updating the map...');

    try {
        const response = await fetch('/api/routes/recommend', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                source,
                destination,
                sourceDisplayName: sourceLocation.displayName || source,
                sourceLatitude: sourceLocation.latitude ?? null,
                sourceLongitude: sourceLocation.longitude ?? null,
                destinationDisplayName: destinationLocation.displayName || destination,
                destinationLatitude: destinationLocation.latitude ?? null,
                destinationLongitude: destinationLocation.longitude ?? null,
                travelDate,
                travelTime,
                userId: defaultUserId
            })
        });

        if (!response.ok) {
            const errorPayload = await response.json().catch(() => null);
            const message = errorPayload?.message || errorPayload?.error || 'Unable to fetch route recommendation.';
            throw new Error(message);
        }

        const data = await response.json();
        if (requestId !== activeSearchToken) {
            return;
        }

        if (!data.routes || !data.routes.length) {
            throw new Error('No route data was returned for the provided search.');
        }

        selectedRouteId = data.recommendedRoute?.routeId || data.routes[0].routeId;
        renderMapRoutes(data.routes);
        const firstRoute = data.routes.find(route => route.routeId === selectedRouteId) || data.routes[0];
        const routeCenter = firstRoute.geometry && firstRoute.geometry.length ? firstRoute.geometry[Math.floor(firstRoute.geometry.length / 2)] : null;

        if (routeCenter) {
            fetchNearbySafeLocations(routeCenter[0], routeCenter[1]);
        }

        const startPoint = sourceLocation.latitude != null && sourceLocation.longitude != null
            ? { latitude: sourceLocation.latitude, longitude: sourceLocation.longitude }
            : (firstRoute.geometry && firstRoute.geometry.length ? { latitude: firstRoute.geometry[0][0], longitude: firstRoute.geometry[0][1] } : null);
        const endPoint = destinationLocation.latitude != null && destinationLocation.longitude != null
            ? { latitude: destinationLocation.latitude, longitude: destinationLocation.longitude }
            : (firstRoute.geometry && firstRoute.geometry.length ? { latitude: firstRoute.geometry[firstRoute.geometry.length - 1][0], longitude: firstRoute.geometry[firstRoute.geometry.length - 1][1] } : null);

        if (startPoint && endPoint) {
            setMapLocationMarkers(startPoint, endPoint);
        }

        showMessage('success', data.message || `${firstRoute.label} is the recommended route based on the available safety model.`);
    } catch (error) {
        console.error(error);
        if (requestId !== activeSearchToken) {
            return;
        }
        routeLayer.clearLayers();
        clearLocationMarkers();
        showMessage('danger', error.message || 'The route search failed. Please check the place names and try again.');
        showWarning('No valid route could be computed for the supplied search.');
    }
}

async function loadEmergencyContacts() {
    try {
        const response = await fetch(`/api/emergency-contacts?userId=${defaultUserId}`);
        if (!response.ok) {
            throw new Error('Unable to load contacts');
        }
        const contacts = await response.json();
        const list = document.getElementById('contactList');
        list.innerHTML = '';

        if (!contacts || !contacts.length) {
            list.innerHTML = '<div class="small text-muted">No emergency contacts saved yet.</div>';
            return;
        }

        contacts.forEach(contact => {
            const item = document.createElement('div');
            item.className = 'contact-list-item';
            item.innerHTML = `
                <div>
                    <strong>${contact.name}</strong>
                    <div class="small text-muted">${contact.relation}</div>
                    <div class="small text-muted">${contact.phone}</div>
                </div>
                <div class="contact-actions">
                    <button type="button" class="btn btn-sm btn-outline-primary edit-contact" data-id="${contact.id}">Edit</button>
                    <button type="button" class="btn btn-sm btn-outline-danger delete-contact" data-id="${contact.id}">Delete</button>
                </div>
            `;
            list.appendChild(item);
        });

        document.querySelectorAll('.edit-contact').forEach(button => {
            button.addEventListener('click', () => startEditContact(Number(button.dataset.id)));
        });
        document.querySelectorAll('.delete-contact').forEach(button => {
            button.addEventListener('click', () => deleteContact(Number(button.dataset.id)));
        });
    } catch (error) {
        console.error(error);
        document.getElementById('contactList').innerHTML = '<div class="small text-danger">Contacts could not be loaded right now.</div>';
    }
}

async function saveEmergencyContact(event) {
    event.preventDefault();
    const payload = {
        userId: defaultUserId,
        name: document.getElementById('contactName').value.trim(),
        phone: document.getElementById('contactPhone').value.trim(),
        relation: document.getElementById('contactRelation').value.trim(),
        isPrimary: true
    };

    const messageBox = document.getElementById('contactMessage');
    try {
        const url = editingContactId ? `/api/emergency-contacts/${editingContactId}` : '/api/emergency-contacts';
        const method = editingContactId ? 'PUT' : 'POST';
        const response = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errorPayload = await response.json().catch(() => null);
            const message = errorPayload?.message || errorPayload?.error || 'Unable to save contact.';
            throw new Error(message);
        }

        messageBox.textContent = editingContactId ? 'Contact updated and saved successfully.' : 'Contact saved successfully and persisted in the database.';
        resetContactForm();
        await loadEmergencyContacts();
    } catch (error) {
        messageBox.textContent = error.message || 'Contact save failed. Please check the values and try again.';
    }
}

async function deleteContact(id) {
    const confirmed = window.confirm('Delete this emergency contact?');
    if (!confirmed) return;

    try {
        const response = await fetch(`/api/emergency-contacts/${id}`, { method: 'DELETE' });
        if (!response.ok) {
            throw new Error('Unable to delete contact');
        }
        document.getElementById('contactMessage').textContent = 'Contact deleted successfully.';
        resetContactForm();
        await loadEmergencyContacts();
    } catch (error) {
        document.getElementById('contactMessage').textContent = error.message || 'Delete failed. Please try again.';
    }
}

function startEditContact(id) {
    const contacts = JSON.parse(localStorage.getItem('saferoute-contacts') || '[]');
    fetch(`/api/emergency-contacts?userId=${defaultUserId}`)
        .then(response => response.ok ? response.json() : [])
        .then(contactList => {
            const contact = (contactList || []).find(item => Number(item.id) === Number(id));
            if (!contact) return;
            document.getElementById('contactName').value = contact.name || '';
            document.getElementById('contactPhone').value = contact.phone || '';
            document.getElementById('contactRelation').value = contact.relation || '';
            editingContactId = Number(id);
            document.getElementById('cancelContactEdit').classList.remove('d-none');
            document.querySelector('#contactForm button[type="submit"]').textContent = 'Update contact';
            document.getElementById('contactMessage').textContent = 'Editing saved contact.';
        });
}

function resetContactForm() {
    editingContactId = null;
    document.getElementById('contactForm').reset();
    document.getElementById('cancelContactEdit').classList.add('d-none');
    document.querySelector('#contactForm button[type="submit"]').textContent = 'Save contact';
}

async function triggerSosDemo() {
    const warningBox = document.getElementById('warningBox');
    try {
        const response = await fetch(`/api/emergency-contacts?userId=${defaultUserId}`);
        const payload = response.ok ? await response.json() : [];
        const contacts = Array.isArray(payload) ? payload : Array.isArray(payload?.value) ? payload.value : [];
        const primaryContact = contacts.length ? contacts[0] : null;
        const message = primaryContact
            ? `Demo SOS activated. Emergency contact: ${primaryContact.name} (${primaryContact.phone}) — ${primaryContact.relation}. This only simulates the alert for the classroom demo.`
            : 'Demo SOS activated. No emergency contact has been saved yet, so this demo shows the alert without a contact.';
        warningBox.className = 'alert alert-danger';
        warningBox.textContent = message;
    } catch (error) {
        warningBox.className = 'alert alert-danger';
        warningBox.textContent = 'Demo SOS activated. This action only simulates an emergency response in the classroom demo.';
    }
}

function setCurrentDateTime() {
    const now = new Date();
    const dateValue = now.toISOString().slice(0, 10);
    const timeValue = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    document.getElementById('travelDate').value = dateValue;
    document.getElementById('travelTimeInput').value = timeValue;
}

const sourceSearchDebounced = debounce(() => handleLocationSearch('source'), 350);
const destinationSearchDebounced = debounce(() => handleLocationSearch('destination'), 350);

document.getElementById('sourceInput').addEventListener('input', sourceSearchDebounced);
document.getElementById('destinationInput').addEventListener('input', destinationSearchDebounced);
document.getElementById('routeForm').addEventListener('submit', handleRouteSubmit);
document.getElementById('contactForm').addEventListener('submit', saveEmergencyContact);
document.getElementById('sosButton').addEventListener('click', triggerSosDemo);
document.getElementById('useCurrentTimeBtn').addEventListener('click', setCurrentDateTime);
document.getElementById('cancelContactEdit').addEventListener('click', resetContactForm);

fetchSafetyZones();
setCurrentDateTime();
loadEmergencyContacts();
fetchNearbySafeLocations(12.9716, 77.5946);
