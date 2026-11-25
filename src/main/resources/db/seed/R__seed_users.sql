INSERT INTO tb_user (login_id, email, name, password, role, ship_id)
SELECT seed.login_id,
       seed.email,
       seed.name,
       '$2a$10$q/AXk6TOk4hgS6Vjhtt6yunDxf5EEKCT88ZS4/oO5vz/8aK.ai796',
       'MEDICAL_OFFICER',
       ship.id
FROM (
    VALUES ('officer_1', 'officer1@example.com', '테스트 의무사관 1', 'SHIP-001'),
           ('officer_2', 'officer2@example.com', '테스트 의무사관 2', 'SHIP-001'),
           ('officer_3', 'officer3@example.com', '테스트 의무사관 3', 'SHIP-002')
) AS seed (login_id, email, name, ship_name)
JOIN tb_ship AS ship ON ship.name = seed.ship_name
-- 이메일 도입 전에 만든 seed 계정에도 이메일을 채운다(design 1.4).
ON CONFLICT ON CONSTRAINT uq_user_login DO UPDATE SET email = EXCLUDED.email;
