-- ============================================================
-- W1 数据库初始化脚本（一次性执行，需管理员权限）
-- 执行方式：在本机 MySQL 客户端以 root 登录后执行本脚本
-- 命令示例（在 Windows 终端，不要把密码发给任何人包括 AI）：
--   mysql -u root -p < D:/graduate/home/docs/init-db.sql
-- 注意：
--   1. 请把下面的 '<请替换为你的开发密码>' 改为你自己的强密码
--   2. 该密码随后写入 realestate-server/.env 的 DB_PASSWORD，
--      .env 不提交 Git
--   3. 应用日常运行使用 realestate_dev，不长期使用 root
-- ============================================================

CREATE DATABASE IF NOT EXISTS realestate
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'realestate_dev'@'localhost'
  IDENTIFIED BY 'Dy97513';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON realestate.* TO 'realestate_dev'@'localhost';

FLUSH PRIVILEGES;

-- 验证（执行后应能查到库和账号）：
--   SHOW DATABASES LIKE 'realestate';
--   SELECT user, host FROM mysql.user WHERE user = 'realestate_dev';
