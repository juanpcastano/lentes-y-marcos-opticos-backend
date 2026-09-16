CREATE TABLE hero_slides (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(150) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    cta_label VARCHAR(50),
    cta_to VARCHAR(255),
    cta2_label VARCHAR(50),
    cta2_to VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hero_slides_active_order ON hero_slides(is_active, sort_order);
