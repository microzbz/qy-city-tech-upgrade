package com.qy.citytechupgrade.tool;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

public final class MysqlToDm8MigrationTool {
    private static final List<String> TABLE_ORDER = List.of(
        "sys_role",
        "sys_user",
        "sys_permission",
        "enterprise_profile",
        "sys_user_role",
        "sys_role_permission",
        "submission_digital_system_option",
        "submission_rd_tool_option",
        "submission_form",
        "submission_basic_info",
        "submission_device_info",
        "submission_digital_info",
        "submission_rd_tool_info",
        "submission_attachment",
        "wf_template",
        "wf_template_node",
        "wf_instance",
        "wf_task",
        "sys_notice",
        "audit_log",
        "industry_process_bind",
        "process_equipment_bind",
        "survey_enterprise_list"
    );
    private static final List<String> CLEAR_ORDER = List.of(
        "sys_user_role",
        "sys_role_permission",
        "submission_attachment",
        "submission_basic_info",
        "submission_device_info",
        "submission_digital_info",
        "submission_rd_tool_info",
        "wf_task",
        "wf_instance",
        "wf_template_node",
        "submission_form",
        "sys_notice",
        "audit_log",
        "submission_digital_system_option",
        "submission_rd_tool_option",
        "survey_enterprise_list",
        "process_equipment_bind",
        "industry_process_bind",
        "wf_template",
        "enterprise_profile",
        "sys_permission",
        "sys_user",
        "sys_role"
    );
    private static final int BATCH_SIZE = 200;

    private MysqlToDm8MigrationTool() {
    }

    public static void main(String[] args) throws Exception {
        MigrationConfig config = MigrationConfig.fromEnv();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Class.forName("dm.jdbc.driver.DmDriver");

        try (Connection mysql = DriverManager.getConnection(config.mysqlUrl(), config.mysqlUsername(), config.mysqlPassword());
             Connection dm = DriverManager.getConnection(config.dmUrl(), config.dmUsername(), config.dmPassword())) {
            mysql.setReadOnly(true);
            dm.setAutoCommit(false);
            verifyDestinationTables(dm);
            clearDestinationTables(dm);
            for (String table : TABLE_ORDER) {
                migrateTable(mysql, dm, table);
            }
            dm.commit();
            System.out.println("MySQL -> DM8 migration completed at " + LocalDateTime.now());
        }
    }

    private static void migrateTable(Connection mysql, Connection dm, String tableName) throws SQLException {
        String selectSql = buildSourceSelectSql(tableName);
        try (Statement queryStatement = mysql.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = queryStatement.executeQuery(selectSql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            if (columnCount <= 0) {
                return;
            }

            String insertSql = buildInsertSql(tableName, meta);
            try (PreparedStatement insert = dm.prepareStatement(insertSql)) {
                setIdentityInsert(dm, tableName, true);
                int currentBatch = 0;
                long total = 0;
                while (rs.next()) {
                    bindRow(insert, rs, meta, columnCount);
                    insert.addBatch();
                    currentBatch++;
                    total++;
                    if (currentBatch >= BATCH_SIZE) {
                        insert.executeBatch();
                        dm.commit();
                        currentBatch = 0;
                    }
                }
                if (currentBatch > 0) {
                    insert.executeBatch();
                    dm.commit();
                }
                setIdentityInsert(dm, tableName, false);
                System.out.printf("Migrated table %-32s rows=%d%n", tableName, total);
            } catch (SQLException ex) {
                setIdentityInsert(dm, tableName, false);
                throw ex;
            }
        }
    }

    private static void clearDestinationTables(Connection dm) throws SQLException {
        for (String tableName : CLEAR_ORDER) {
            long deleted = clearDestination(dm, tableName);
            System.out.printf("Cleared DM8 table %-32s deleted=%d%n", tableName, deleted);
        }
        dm.commit();
    }

    private static long clearDestination(Connection dm, String tableName) throws SQLException {
        try (Statement statement = dm.createStatement()) {
            return statement.executeLargeUpdate("DELETE FROM " + tableName);
        }
    }

    private static void setIdentityInsert(Connection dm, String tableName, boolean enabled) throws SQLException {
        try (Statement statement = dm.createStatement()) {
            statement.execute("set identity_insert " + tableName + ' ' + (enabled ? "on" : "off"));
        }
    }

    private static String buildInsertSql(String tableName, ResultSetMetaData meta) throws SQLException {
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (i > 1) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(meta.getColumnName(i));
            placeholders.append('?');
        }
        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    private static String buildSourceSelectSql(String tableName) {
        return switch (tableName) {
            case "sys_user_role" ->
                "SELECT x.* FROM sys_user_role x INNER JOIN sys_user u ON u.id = x.user_id INNER JOIN sys_role r ON r.id = x.role_id";
            case "sys_role_permission" ->
                "SELECT x.* FROM sys_role_permission x INNER JOIN sys_role r ON r.id = x.role_id INNER JOIN sys_permission p ON p.id = x.permission_id";
            case "submission_form" ->
                "SELECT x.* FROM submission_form x INNER JOIN enterprise_profile e ON e.id = x.enterprise_id";
            case "submission_basic_info" ->
                "SELECT x.* FROM submission_basic_info x INNER JOIN submission_form f ON f.id = x.submission_id";
            case "submission_device_info" ->
                "SELECT x.* FROM submission_device_info x INNER JOIN submission_form f ON f.id = x.submission_id";
            case "submission_digital_info" ->
                "SELECT x.* FROM submission_digital_info x INNER JOIN submission_form f ON f.id = x.submission_id";
            case "submission_rd_tool_info" ->
                "SELECT x.* FROM submission_rd_tool_info x INNER JOIN submission_form f ON f.id = x.submission_id";
            case "submission_attachment" ->
                "SELECT x.* FROM submission_attachment x INNER JOIN submission_form f ON f.id = x.submission_id";
            case "wf_template_node" ->
                "SELECT x.* FROM wf_template_node x INNER JOIN wf_template t ON t.id = x.template_id";
            case "wf_instance" ->
                "SELECT x.* FROM wf_instance x INNER JOIN wf_template t ON t.id = x.template_id";
            case "wf_task" ->
                "SELECT x.* FROM wf_task x INNER JOIN wf_instance i ON i.id = x.instance_id";
            default -> "SELECT * FROM " + tableName;
        };
    }

    private static void bindRow(PreparedStatement insert, ResultSet rs, ResultSetMetaData meta, int columnCount) throws SQLException {
        for (int i = 1; i <= columnCount; i++) {
            int columnType = meta.getColumnType(i);
            Object value = rs.getObject(i);
            if (value == null) {
                insert.setNull(i, columnType);
                continue;
            }
            if (value instanceof Timestamp timestamp) {
                insert.setTimestamp(i, timestamp);
                continue;
            }
            if (columnType == Types.BIT || columnType == Types.BOOLEAN) {
                insert.setObject(i, rs.getBoolean(i) ? 1 : 0);
                continue;
            }
            insert.setObject(i, value);
        }
    }

    private static void verifyDestinationTables(Connection dm) throws SQLException {
        DatabaseMetaData metaData = dm.getMetaData();
        for (String tableName : TABLE_ORDER) {
            try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
                if (!rs.next()) {
                    throw new IllegalStateException("DM8 destination table missing: " + tableName);
                }
            }
        }
    }

    private record MigrationConfig(
        String mysqlUrl,
        String mysqlUsername,
        String mysqlPassword,
        String dmUrl,
        String dmUsername,
        String dmPassword
    ) {
        private static MigrationConfig fromEnv() {
            return new MigrationConfig(
                require("MYSQL_URL"),
                require("MYSQL_USERNAME"),
                require("MYSQL_PASSWORD"),
                require("DM_URL"),
                require("DM_USERNAME"),
                require("DM_PASSWORD")
            );
        }

        private static String require(String key) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing environment variable: " + key);
            }
            return value;
        }
    }
}
