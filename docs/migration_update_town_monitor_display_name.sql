UPDATE sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
SET u.display_name = u.username,
    u.updated_at = NOW()
WHERE r.role_code = 'TOWN_MONITOR'
  AND u.display_name <> u.username;
