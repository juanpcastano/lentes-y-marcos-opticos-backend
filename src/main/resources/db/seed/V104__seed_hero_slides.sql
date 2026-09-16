-- Seed del hero (solo dev). Excluir de prod via spring.flyway.locations.

INSERT INTO hero_slides (id, title, description, image_url, cta_label, cta_to, cta2_label, cta2_to, sort_order, is_active) VALUES
('60000000-0000-4000-8000-000000000001', 'Nueva Colección de Lentes 2025', 'Descubre los modelos más exclusivos de las mejores marcas. Estilo y protección para tu vista.', 'https://images.unsplash.com/photo-1574258495973-f010dfbb5371?auto=format&fit=crop&w=1600&q=80', 'Ver catálogo', '/catalog', 'Agendar cita', '/appointments', 0, TRUE),
('60000000-0000-4000-8000-000000000002', 'Promoción de Verano', '20% de descuento en lentes de sol polarizados. Solo por tiempo limitado.', 'https://images.unsplash.com/photo-1511499767150-a48a237f0083?auto=format&fit=crop&w=1600&q=80', 'Ver ofertas', '/catalog', NULL, NULL, 1, TRUE),
('60000000-0000-4000-8000-000000000003', 'Examen Visual Profesional', 'Contamos con optometristas certificados y tecnología de última generación para cuidar tu salud visual.', 'https://images.unsplash.com/photo-1559563458-527698bf5295?auto=format&fit=crop&w=1600&q=80', 'Reservar examen', '/appointments', NULL, NULL, 2, TRUE)
;
