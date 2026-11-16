INSERT INTO tb_user (login_id, name, password, role, ship_id)
SELECT seed.login_id,
       seed.name,
       '$2a$10$q/AXk6TOk4hgS6Vjhtt6yunDxf5EEKCT88ZS4/oO5vz/8aK.ai796',
       'MEDICAL_OFFICER',
       ship.id
FROM (
    VALUES ('officer_1', '테스트 의무사관 1', 'SHIP-001'),
           ('officer_2', '테스트 의무사관 2', 'SHIP-001'),
           ('officer_3', '테스트 의무사관 3', 'SHIP-002')
) AS seed (login_id, name, ship_name)
JOIN tb_ship AS ship ON ship.name = seed.ship_name
ON CONFLICT ON CONSTRAINT uq_user_login DO NOTHING;
