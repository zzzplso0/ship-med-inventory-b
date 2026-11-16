INSERT INTO tb_ship (name)
SELECT seed.name
FROM (
    SELECT 'SHIP-' || LPAD(n::text, 3, '0') AS name
    FROM generate_series(1, 100) AS n
) AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_ship AS ship
    WHERE ship.name = seed.name
);
