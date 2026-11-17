INSERT INTO users (id, name, email, phone, password_hash) VALUES
(1, 'Demo User', 'demo@example.com', '+91 98765 43210', '$2a$10$7j5NXoJ6eQX7Z5KXnK7mCeM8m0m2Q7h3j0z6eZ4s9zL7k6S6H1h2')
ON DUPLICATE KEY UPDATE name = VALUES(name), email = VALUES(email), phone = VALUES(phone), password_hash = VALUES(password_hash);

INSERT INTO safety_zones (id, name, description, latitude, longitude, radius_km, risk_level, active) VALUES
(1, 'Demo Risk Zone A', 'Demo high-risk area with poor lighting and isolated roads.', 12.9770, 77.6060, 1.2, 'High Risk', TRUE),
(2, 'Demo Risk Zone B', 'Late-night isolated route with minimal public activity.', 12.9725, 77.5915, 1.0, 'Moderate Risk', TRUE),
(3, 'Demo Risk Zone C', 'Low footfall area near a poorly lit underpass.', 12.9808, 77.6004, 1.3, 'High Risk', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), latitude = VALUES(latitude), longitude = VALUES(longitude), radius_km = VALUES(radius_km), risk_level = VALUES(risk_level), active = VALUES(active);

INSERT INTO emergency_contacts (id, user_id, name, phone, relation, is_primary) VALUES
(1, 1, 'Mother', '+91 99887 66554', 'Parent', TRUE),
(2, 1, 'Friend', '+91 91234 56789', 'Friend', FALSE)
ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone), relation = VALUES(relation), is_primary = VALUES(is_primary);

INSERT INTO route_search_history (id, user_id, source_location, destination_location, route_label, safety_score, risk_level, travel_time_minutes, distance_km) VALUES
(1, 1, 'Campus Gate', 'City Library', 'Main Street Safe Route', 82.0, 'Low Risk', 18, 4.8),
(2, 1, 'Cinema Hall', 'Metro Station', 'Park Connector Route', 61.0, 'Moderate Risk', 31, 5.6)
ON DUPLICATE KEY UPDATE source_location = VALUES(source_location), destination_location = VALUES(destination_location), route_label = VALUES(route_label), safety_score = VALUES(safety_score), risk_level = VALUES(risk_level), travel_time_minutes = VALUES(travel_time_minutes), distance_km = VALUES(distance_km);
