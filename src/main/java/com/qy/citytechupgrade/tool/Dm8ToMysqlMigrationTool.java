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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class Dm8ToMysqlMigrationTool {
    private static final List<String> PREFERRED_TABLE_ORDER = List.of(
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
    private static final int BATCH_SIZE = 200;

    private Dm8ToMysqlMigrationTool() {
    }

    public static void main(String[] args) throws Exception {
        MigrationConfig config = MigrationConfig.fromEnv();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Class.forName("dm.jdbc.driver.DmDriver");

        try (Connection dm = DriverManager.getConnection(config.dmUrl(), config.dmUsername(), config.dmPassword());
             Connection mysqlAdmin = DriverManager.getConnection(config.mysqlAdminUrl(), config.mysqlUsername(), config.mysqlPassword())) {
            Map<String, TableDef> tables = loadSourceTables(dm, config.dmSchema());
            if (tables.isEmpty()) {
                throw new IllegalStateException("No tables found in DM8 schema: " + config.dmSchema());
            }
            recreateDatabase(mysqlAdmin, config.mysqlDatabase());
            try (Connection mysql = DriverManager.getConnection(config.mysqlUrl(), config.mysqlUsername(), config.mysqlPassword())) {
                mysql.setAutoCommit(false);
                createDestinationTables(mysql, tables);
                for (TableDef table : orderedTables(tables)) {
                    migrateTable(dm, mysql, config.dmSchema(), table);
                }
                mysql.commit();
                verifyCounts(dm, mysql, config.dmSchema(), orderedTables(tables));
            }
        }

        System.out.println("DM8 -> MySQL migration completed at " + LocalDateTime.now());
    }

    private static Map<String, TableDef> loadSourceTables(Connection dm, String schema) throws SQLException {
        DatabaseMetaData metaData = dm.getMetaData();
        Map<String, TableDef> tables = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String sourceName = rs.getString("TABLE_NAME");
                if (sourceName == null || sourceName.startsWith("##")) {
                    continue;
                }
                TableDef table = new TableDef(sourceName);
                tables.put(table.targetName(), table);
            }
        }
        for (TableDef table : tables.values()) {
            loadColumns(metaData, schema, table);
            loadPrimaryKeys(metaData, schema, table);
            loadIndexes(metaData, schema, table);
        }
        return tables;
    }

    private static void loadColumns(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, schema, table.sourceName(), "%")) {
            while (rs.next()) {
                table.columns().add(new ColumnDef(
                    rs.getString("COLUMN_NAME"),
                    rs.getInt("DATA_TYPE"),
                    rs.getString("TYPE_NAME"),
                    rs.getInt("COLUMN_SIZE"),
                    rs.getInt("DECIMAL_DIGITS"),
                    rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                    "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")),
                    rs.getInt("ORDINAL_POSITION")
                ));
            }
        }
        table.columns().sort(Comparator.comparingInt(ColumnDef::ordinalPosition));
    }

    private static void loadPrimaryKeys(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        Map<Short, String> pkColumns = new TreeMap<>();
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, table.sourceName())) {
            while (rs.next()) {
                pkColumns.put(rs.getShort("KEY_SEQ"), normalizeName(rs.getString("COLUMN_NAME")));
            }
        }
        table.primaryKeyColumns().addAll(pkColumns.values());
    }

    private static void loadIndexes(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        Map<String, IndexDef> indexes = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(null, schema, table.sourceName(), false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                short type = rs.getShort("TYPE");
                if (indexName == null || columnName == null || type == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String normalizedIndexName = normalizeName(indexName);
                if (normalizedIndexName.equals("primary")) {
                    continue;
                }
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                IndexDef index = indexes.computeIfAbsent(normalizedIndexName, key -> new IndexDef(normalizedIndexName, unique));
                index.columnsByPosition().put(rs.getShort("ORDINAL_POSITION"), normalizeName(columnName));
            }
        }
        for (IndexDef index : indexes.values()) {
            List<String> indexColumns = new ArrayList<>(index.columnsByPosition().values());
            if (indexColumns.equals(table.primaryKeyColumns())) {
                continue;
            }
            table.indexes().add(index);
        }
    }

    private static void recreateDatabase(Connection mysqlAdmin, String databaseName) throws SQLException {
        try (Statement statement = mysqlAdmin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            statement.execute("CREATE DATABASE `" + databaseName + "` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci");
        }
        System.out.println("Recreated MySQL database: " + databaseName);
    }

    private static void createDestinationTables(Connection mysql, Map<String, TableDef> tables) throws SQLException {
        for (TableDef table : orderedTables(tables)) {
            try (Statement statement = mysql.createStatement()) {
                statement.execute(buildCreateTableSql(table));
                for (String indexSql : buildCreateIndexSql(table)) {
                    statement.execute(indexSql);
                }
            }
            System.out.println("Prepared table: " + table.targetName());
        }
        mysql.commit();
    }

    private static List<TableDef> orderedTables(Map<String, TableDef> tables) {
        return tables.values().stream()
            .sorted(Comparator
                .comparingInt((TableDef t) -> {
                    int index = PREFERRED_TABLE_ORDER.indexOf(t.targetName());
                    return index >= 0 ? index : Integer.MAX_VALUE;
                })
                .thenComparing(TableDef::targetName))
            .toList();
    }

    private static String buildCreateTableSql(TableDef table) {
        List<String> definitions = new ArrayList<>();
        for (ColumnDef column : table.columns()) {
            StringBuilder definition = new StringBuilder();
            definition.append("`").append(column.targetName()).append("` ");
            definition.append(toMysqlType(column));
            if (!column.nullable()) {
                definition.append(" NOT NULL");
            }
            if (column.autoIncrement()) {
                definition.append(" AUTO_INCREMENT");
            }
            definitions.add(definition.toString());
        }
        if (!table.primaryKeyColumns().isEmpty()) {
            definitions.add("PRIMARY KEY (" + joinQuoted(table.primaryKeyColumns()) + ")");
        }
        return "CREATE TABLE `" + table.targetName() + "` (\n    "
            + String.join(",\n    ", definitions)
            + "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";
    }

    private static List<String> buildCreateIndexSql(TableDef table) {
        Set<String> emitted = new LinkedHashSet<>();
        List<String> sqlList = new ArrayList<>();
        for (IndexDef index : table.indexes()) {
            List<String> columns = new ArrayList<>(index.columnsByPosition().values());
            if (columns.isEmpty()) {
                continue;
            }
            String baseName = index.name().length() > 60 ? index.name().substring(0, 60) : index.name();
            String finalName = baseName;
            int suffix = 1;
            while (!emitted.add(finalName)) {
                finalName = baseName + "_" + suffix++;
            }
            sqlList.add("CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX `" + finalName + "` ON `"
                + table.targetName() + "` (" + joinIndexColumns(table, columns) + ")");
        }
        return sqlList;
    }

    private static void migrateTable(Connection dm, Connection mysql, String schema, TableDef table) throws SQLException {
        String selectSql = buildSourceSelectSql(schema, table.targetName());
        try (Statement queryStatement = dm.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = queryStatement.executeQuery(selectSql)) {
            ResultSetMetaData meta = rs.getMetaData();
            if (meta.getColumnCount() == 0) {
                return;
            }
            String insertSql = buildInsertSql(table);
            try (PreparedStatement insert = mysql.prepareStatement(insertSql)) {
                int currentBatch = 0;
                long total = 0;
                while (rs.next()) {
                    bindRow(insert, rs, meta, table.columns());
                    insert.addBatch();
                    currentBatch++;
                    total++;
                    if (currentBatch >= BATCH_SIZE) {
                        insert.executeBatch();
                        mysql.commit();
                        currentBatch = 0;
                    }
                }
                if (currentBatch > 0) {
                    insert.executeBatch();
                    mysql.commit();
                }
                System.out.printf("Migrated table %-32s rows=%d%n", table.targetName(), total);
            }
        }
    }

    private static String buildInsertSql(TableDef table) {
        List<String> columns = table.columns().stream().map(ColumnDef::targetName).toList();
        String placeholders = columns.stream().map(col -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        return "INSERT INTO `" + table.targetName() + "` (" + joinQuoted(columns) + ") VALUES (" + placeholders + ")";
    }

    private static String buildSourceSelectSql(String schema, String tableName) {
        String t = qualifiedSourceName(schema, tableName);
        return switch (tableName) {
            case "sys_user_role" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "sys_user") + " u ON u.id = x.user_id"
                    + " INNER JOIN " + qualifiedSourceName(schema, "sys_role") + " r ON r.id = x.role_id";
            case "sys_role_permission" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "sys_role") + " r ON r.id = x.role_id"
                    + " INNER JOIN " + qualifiedSourceName(schema, "sys_permission") + " p ON p.id = x.permission_id";
            case "submission_form" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "enterprise_profile") + " e ON e.id = x.enterprise_id";
            case "submission_basic_info" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "submission_form") + " f ON f.id = x.submission_id";
            case "submission_device_info" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "submission_form") + " f ON f.id = x.submission_id";
            case "submission_digital_info" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "submission_form") + " f ON f.id = x.submission_id";
            case "submission_rd_tool_info" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "submission_form") + " f ON f.id = x.submission_id";
            case "submission_attachment" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "submission_form") + " f ON f.id = x.submission_id";
            case "wf_template_node" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "wf_template") + " w ON w.id = x.template_id";
            case "wf_instance" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "wf_template") + " w ON w.id = x.template_id";
            case "wf_task" ->
                "SELECT x.* FROM " + t + " x INNER JOIN " + qualifiedSourceName(schema, "wf_instance") + " i ON i.id = x.instance_id";
            default -> "SELECT * FROM " + t;
        };
    }

    private static String qualifiedSourceName(String schema, String tableName) {
        return schema + "." + tableName;
    }

    private static void bindRow(PreparedStatement insert, ResultSet rs, ResultSetMetaData meta, List<ColumnDef> columns) throws SQLException {
        for (int i = 1; i <= columns.size(); i++) {
            int sourceType = meta.getColumnType(i);
            Object value = rs.getObject(i);
            if (value == null) {
                insert.setNull(i, toMysqlNullType(sourceType));
                continue;
            }
            if (value instanceof Timestamp timestamp) {
                insert.setTimestamp(i, timestamp);
                continue;
            }
            if (sourceType == Types.BIT || sourceType == Types.BOOLEAN || sourceType == Types.TINYINT || sourceType == Types.SMALLINT) {
                insert.setBoolean(i, rs.getInt(i) != 0);
                continue;
            }
            insert.setObject(i, value);
        }
    }

    private static int toMysqlNullType(int sourceType) {
        return switch (sourceType) {
            case Types.BIT, Types.BOOLEAN, Types.TINYINT, Types.SMALLINT -> Types.TINYINT;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Types.TIMESTAMP;
            case Types.DATE -> Types.DATE;
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR -> Types.LONGVARCHAR;
            case Types.DECIMAL, Types.NUMERIC -> Types.DECIMAL;
            default -> sourceType;
        };
    }

    private static void verifyCounts(Connection dm, Connection mysql, String schema, List<TableDef> tables) throws SQLException {
        for (TableDef table : tables) {
            long sourceCount = count(dm, "SELECT COUNT(*) FROM " + qualifiedSourceName(schema, table.targetName()));
            long targetCount = count(mysql, "SELECT COUNT(*) FROM `" + table.targetName() + "`");
            if (sourceCount != targetCount) {
                throw new IllegalStateException("Row count mismatch for " + table.targetName()
                    + ": source=" + sourceCount + ", target=" + targetCount);
            }
            System.out.printf("Verified table %-32s rows=%d%n", table.targetName(), targetCount);
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String toMysqlType(ColumnDef column) {
        return switch (column.jdbcType()) {
            case Types.BIGINT -> "BIGINT";
            case Types.INTEGER -> "INT";
            case Types.SMALLINT, Types.TINYINT, Types.BIT, Types.BOOLEAN -> "BIT";
            case Types.DECIMAL, Types.NUMERIC -> "DECIMAL(" + safePrecision(column.size(), 18) + "," + Math.max(column.decimalDigits(), 0) + ")";
            case Types.CHAR -> "CHAR(" + safePrecision(column.size(), 1) + ")";
            case Types.VARCHAR, Types.NVARCHAR -> "VARCHAR(" + safePrecision(column.size(), 255) + ")";
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "LONGTEXT";
            case Types.DATE -> "DATE";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "DATETIME(6)";
            case Types.DOUBLE -> "DOUBLE";
            case Types.FLOAT, Types.REAL -> "FLOAT";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "LONGBLOB";
            default -> {
                if (column.typeName() != null && column.typeName().toUpperCase(Locale.ROOT).contains("TEXT")) {
                    yield "LONGTEXT";
                }
                throw new IllegalArgumentException("Unsupported DM8 column type " + column.typeName()
                    + " for " + column.sourceName());
            }
        };
    }

    private static int safePrecision(int size, int fallback) {
        return size > 0 ? size : fallback;
    }

    private static String joinQuoted(List<String> names) {
        return names.stream().map(name -> "`" + name + "`").reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String joinIndexColumns(TableDef table, List<String> columns) {
        List<String> rendered = new ArrayList<>();
        for (String columnName : columns) {
            ColumnDef column = table.columnByTargetName(columnName);
            if (column == null) {
                rendered.add("`" + columnName + "`");
                continue;
            }
            rendered.add(renderIndexColumn(column));
        }
        return String.join(", ", rendered);
    }

    private static String renderIndexColumn(ColumnDef column) {
        int jdbcType = column.jdbcType();
        if (jdbcType == Types.VARCHAR || jdbcType == Types.NVARCHAR || jdbcType == Types.CHAR) {
            if (column.size() > 255) {
                return "`" + column.targetName() + "`(255)";
            }
            return "`" + column.targetName() + "`";
        }
        if (jdbcType == Types.LONGVARCHAR || jdbcType == Types.LONGNVARCHAR || jdbcType == Types.CLOB || jdbcType == Types.NCLOB) {
            return "`" + column.targetName() + "`(255)";
        }
        return "`" + column.targetName() + "`";
    }

    private static String normalizeName(String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private record MigrationConfig(
        String mysqlUrl,
        String mysqlAdminUrl,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String dmUrl,
        String dmUsername,
        String dmPassword,
        String dmSchema
    ) {
        private static MigrationConfig fromEnv() {
            String mysqlUrl = require("MYSQL_URL");
            String mysqlUsername = require("MYSQL_USERNAME");
            String mysqlPassword = require("MYSQL_PASSWORD");
            String mysqlDatabase = resolveMysqlDatabase(mysqlUrl);
            return new MigrationConfig(
                mysqlUrl,
                buildMysqlAdminUrl(mysqlUrl),
                mysqlDatabase,
                mysqlUsername,
                mysqlPassword,
                require("DM_URL"),
                require("DM_USERNAME"),
                require("DM_PASSWORD"),
                getenvOrDefault("DM_SCHEMA", "CITY_UPGRADE")
            );
        }

        private static String resolveMysqlDatabase(String mysqlUrl) {
            String withoutPrefix = mysqlUrl.substring("jdbc:mysql://".length());
            int slashIndex = withoutPrefix.indexOf('/');
            if (slashIndex < 0) {
                throw new IllegalArgumentException("MYSQL_URL missing database name: " + mysqlUrl);
            }
            String dbAndQuery = withoutPrefix.substring(slashIndex + 1);
            if (dbAndQuery.isBlank()) {
                throw new IllegalArgumentException("MYSQL_URL missing database name: " + mysqlUrl);
            }
            int queryIndex = dbAndQuery.indexOf('?');
            return queryIndex >= 0 ? dbAndQuery.substring(0, queryIndex) : dbAndQuery;
        }

        private static String buildMysqlAdminUrl(String mysqlUrl) {
            String withoutPrefix = mysqlUrl.substring("jdbc:mysql://".length());
            int slashIndex = withoutPrefix.indexOf('/');
            if (slashIndex < 0) {
                return mysqlUrl;
            }
            int queryIndex = withoutPrefix.indexOf('?', slashIndex);
            String hostPort = withoutPrefix.substring(0, slashIndex);
            String query = queryIndex >= 0 ? withoutPrefix.substring(queryIndex) : "";
            return "jdbc:mysql://" + hostPort + "/" + query;
        }

        private static String require(String key) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing environment variable: " + key);
            }
            return value;
        }

        private static String getenvOrDefault(String key, String fallback) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    private static final class TableDef {
        private final String sourceName;
        private final String targetName;
        private final List<ColumnDef> columns = new ArrayList<>();
        private final List<String> primaryKeyColumns = new ArrayList<>();
        private final List<IndexDef> indexes = new ArrayList<>();

        private TableDef(String sourceName) {
            this.sourceName = sourceName;
            this.targetName = normalizeName(sourceName);
        }

        public String sourceName() {
            return sourceName;
        }

        public String targetName() {
            return targetName;
        }

        public List<ColumnDef> columns() {
            return columns;
        }

        public List<String> primaryKeyColumns() {
            return primaryKeyColumns;
        }

        public List<IndexDef> indexes() {
            return indexes;
        }

        public ColumnDef columnByTargetName(String targetName) {
            for (ColumnDef column : columns) {
                if (column.targetName().equals(targetName)) {
                    return column;
                }
            }
            return null;
        }
    }

    private record ColumnDef(
        String sourceName,
        int jdbcType,
        String typeName,
        int size,
        int decimalDigits,
        boolean nullable,
        boolean autoIncrement,
        int ordinalPosition
    ) {
        private String targetName() {
            return normalizeName(sourceName);
        }
    }

    private static final class IndexDef {
        private final String name;
        private final boolean unique;
        private final Map<Short, String> columnsByPosition = new TreeMap<>();

        private IndexDef(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        public String name() {
            return name;
        }

        public boolean unique() {
            return unique;
        }

        public Map<Short, String> columnsByPosition() {
            return columnsByPosition;
        }
    }
}
