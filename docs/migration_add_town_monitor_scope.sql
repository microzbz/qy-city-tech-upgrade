SET @schema_name = DATABASE();

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = @schema_name
       AND table_name = 'survey_enterprise_list'
       AND column_name = 'town_street_code') = 0,
    'ALTER TABLE survey_enterprise_list ADD COLUMN town_street_code VARCHAR(2) NULL COMMENT ''镇街编号'' AFTER town_park',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = @schema_name
       AND table_name = 'survey_enterprise_list'
       AND index_name = 'idx_survey_town_street_code') = 0,
    'CREATE INDEX idx_survey_town_street_code ON survey_enterprise_list(town_street_code)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = @schema_name
       AND table_name = 'sys_user'
       AND column_name = 'town_street_code_scope') = 0,
    'ALTER TABLE sys_user ADD COLUMN town_street_code_scope VARCHAR(2) NULL AFTER enterprise_code_first_digit_scope',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS town_monitor_seed;
CREATE TEMPORARY TABLE town_monitor_seed (
    town_code VARCHAR(2) PRIMARY KEY,
    town_name VARCHAR(32) NOT NULL,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

INSERT INTO town_monitor_seed (town_code, town_name, username, password_hash) VALUES
('01','滨海湾','bhw_monitor','$2a$10$f7ACB0wZMaOGeRiOIqwVp.I4eqyve.PKH65F9.zy.KhP71Rv5DZz6'),
('02','常平镇','cp_monitor','$2a$10$fEa8e4eikabTYCf0mcreduRr4WjlFHN/lzYRcK7iWIi4kVDGruJGK'),
('03','大朗镇','dl_monitor','$2a$10$WYTbb3y3WnCi9lrGPPIfUeowe79DdR3IzArPuVANCeosPAIEMzT62'),
('04','大岭山镇','dls_monitor','$2a$10$MNaV3P266ZrkIg7b6tj2BebcwheoZ9UbB7xj/Ee3aySuRQFZ28qMa'),
('05','东城街道','dc_monitor','$2a$10$qV70SYBQxdIvaqfjiu4KhuZalTOU7FddLkUgl2LVKbRU3NnUNAki2'),
('06','东坑镇','dk_monitor','$2a$10$FDiejyyGzQRTriAqMzNmU.CPEO0m6seisekacgUBLmeXny5QfCShO'),
('07','洪梅镇','hm07_monitor','$2a$10$3V3BKem1kJU54hjd4stt2ubSBGPzKl3Gw8Jt2qoeNCJVxtIfWxGk.'),
('08','厚街镇','hj08_monitor','$2a$10$pl/fVSZpe5F03xbsaScJ0OzLLkuHgtoLrIwVI0Z.ZnvfrPQjw3Rfe'),
('09','虎门镇','hm09_monitor','$2a$10$xcADWU8KJjsXHSE96M0EgejDZPJJ2pnbmz0JqKnYySwlEYdr.nVkq'),
('10','寮步镇','lb_monitor','$2a$10$YQnh0UcvkVPX5ChzHfJ9XOx5/PwXaB3HMqdXnUexvED6x2babn5Ca'),
('11','麻涌镇','mc_monitor','$2a$10$BsH4TQvi1F1Hp7ZVoJLEY.EMQNUMjuAnQNOL58V7v3/EtF1wSYIDi'),
('12','南城街道','nc_monitor','$2a$10$mDCm7fxa2UEC/PeLQInSuehxVw2GSSx4YnkGYL55Ebw8Zoa4YrW8C'),
('13','沙田镇','st_monitor','$2a$10$ttzLfUS4Z.PBMlIYk5HjpOLVVWU6XvpuoyxkdRoxFbIfuPNIbneZG'),
('14','松山湖','ssh_monitor','$2a$10$21FsNPPAjbRWqhP0XpP9iOzCaZ5Qs/duQh3yMORTmVYat9oDZxeXy'),
('15','望牛墩镇','wnd_monitor','$2a$10$6z1JwIrBFnWsnWODLmt28ugGXhv2ttUDw0INQuyRZHGiUfKxxCcTS'),
('16','长安镇','ca_monitor','$2a$10$4C.Hg.irvSh6SE/pDUg9vu6ZzU4cKHDLEvDVHRCj5X4dt/f.H4GoC'),
('17','中堂镇','zt_monitor','$2a$10$b/II.RzZ0dN4oTioNLp9iOSgSZLe5oHyRAU61jrG0eruCtzp7DwvO'),
('18','茶山镇','cs_monitor','$2a$10$iThL2gleN4xcVl0GSD/cM.lGguocoQEBuyBzSr8Fr.9nqkHynjEFC'),
('19','道滘镇','dj_monitor','$2a$10$1AkmTgKFPLMPesR3b21z8ujWaZHNJJrAa7/LuOUTa92fGqtcbP61i'),
('20','凤岗镇','fg_monitor','$2a$10$DMT6WKaBud53KrHHlgPWAe2tytFp/QkvgS7MbG7Fh9OK9QtT1m89i'),
('21','高埗镇','gb_monitor','$2a$10$JOUhsUpqM6GVLpZO/Ipy1OdDB07LsF5GNOaDfWEtl0.dg58ny80L6'),
('22','莞城街道','gc_monitor','$2a$10$KPfsknlaRio3.6JsyMhb5O9avLeIbgZDR4WGn3cs.OQsIvKnbtBci'),
('23','横沥镇','hl_monitor','$2a$10$Dn1vrR0W2/sxX5odfBwG8e8JpxpZ7nt8mRkAZcmQxEsOFXcM670/e'),
('24','黄江镇','hj24_monitor','$2a$10$5Gnuf.zpvrJqrkquVVoGjOBy.YF5wv3c1NXyxahsghNMRn.AVTblG'),
('25','企石镇','qs_monitor','$2a$10$jrn8PZ3HVAeX8qCPK2mjYOYWav4Rn2M4kf.3Kra3D8FyKE7W5tMcq'),
('26','桥头镇','qt_monitor','$2a$10$TgM4iytN7ZZ1MG/3dvrg6.qlgFlXQspzU1WsiiQRUPCvTjC7Q8Cp6'),
('27','清溪镇','qx_monitor','$2a$10$mJGZDauJpSGneEsFeQJu4.4hVJ3WzqIzyBssd6.RSWG8n78nQwu4K'),
('28','石碣镇','sj_monitor','$2a$10$kKyJM4KPg27UWSMNdVpboOk2hxELIcWQF741Lwxl/pVwtGxtzKQiS'),
('29','石龙镇','sl_monitor','$2a$10$A/1Nu03PDGtkTTz/u/Wr9eH6FBWhU13fWaes/ZdQq4yx9MUBU2Ose'),
('30','石排镇','sp_monitor','$2a$10$zZRHG8jgEs.BgPJbICsvEO1kik9Ywq1oAmvss5XjelDWLbXsIdD.a'),
('31','塘厦镇','tx_monitor','$2a$10$Wxfa35kXe75QuBNJM7plAO3FTw1VopVspOzn.5FhCLIRFM7ODFlEK'),
('32','万江街道','wj_monitor','$2a$10$x0MnL8nuq8f9HUn6WlZo1.QXFMy7lbz4kDCIc3XXR397rf9O/ChQC'),
('33','谢岗镇','xg_monitor','$2a$10$V6Z889F2vadmCs3KdAfOluqY6vA4/c94S48OVtZWlAK8UQUlR.n8e'),
('34','樟木头镇','zmt_monitor','$2a$10$9TtYj6jM6hjys7khemlXHOMTZiWycnYsJu9naRWWa/eU2wyyrsiOi');

UPDATE survey_enterprise_list s
JOIN town_monitor_seed t ON s.town_park = t.town_name
SET s.town_street_code = t.town_code;

INSERT INTO sys_role (role_code, role_name, created_at, updated_at)
VALUES ('TOWN_MONITOR', '镇街查阅账号', NOW(), NOW())
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), updated_at = NOW();

INSERT INTO sys_user (
    username,
    password_hash,
    display_name,
    status,
    enterprise_id,
    enterprise_code_first_digit_scope,
    town_street_code_scope,
    created_at,
    updated_at
)
SELECT
    username,
    password_hash,
    username,
    'ACTIVE',
    NULL,
    NULL,
    town_code,
    NOW(),
    NOW()
FROM town_monitor_seed
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    display_name = VALUES(display_name),
    status = 'ACTIVE',
    enterprise_id = NULL,
    enterprise_code_first_digit_scope = NULL,
    town_street_code_scope = VALUES(town_street_code_scope),
    updated_at = NOW();

DELETE ur
FROM sys_user_role ur
JOIN sys_user u ON ur.user_id = u.id
JOIN sys_role r ON ur.role_id = r.id
JOIN town_monitor_seed t ON u.username = t.username
WHERE r.role_code <> 'TOWN_MONITOR';

INSERT INTO sys_user_role (user_id, role_id, created_at)
SELECT u.id, r.id, NOW()
FROM town_monitor_seed t
JOIN sys_user u ON u.username = t.username
JOIN sys_role r ON r.role_code = 'TOWN_MONITOR'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user_role ur
    WHERE ur.user_id = u.id
      AND ur.role_id = r.id
);

DROP TEMPORARY TABLE IF EXISTS town_monitor_seed;
