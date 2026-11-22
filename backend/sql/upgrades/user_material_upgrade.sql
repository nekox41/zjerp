CREATE TABLE IF NOT EXISTS sys_user_material (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 material_key VARCHAR(100) NOT NULL,
 value_type VARCHAR(16) NOT NULL,
 content VARCHAR(4000) NOT NULL,
 UNIQUE KEY uk_user_material(user_id,material_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
