package com.qy.citytechupgrade.tool;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Dm8InitSqlExportTool {
    private static final String APPLICATION_YML = "src/main/resources/application.yml";
    private static final String OUTPUT_SQL = "sql/dm8/prod/01_init_from_live_dm8.sql";
    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper("${", "}", ":", true);
    private static final DateTimeFormatter HEADER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.ROOT);
    private static final Set<String> DATA_EXCLUDED_TABLES = Set.of(
        "AUDIT_LOG",
        "SUBMISSION_ATTACHMENT",
        "SUBMISSION_BASIC_INFO",
        "SUBMISSION_DEVICE_INFO",
        "SUBMISSION_DIGITAL_INFO",
        "SUBMISSION_FORM",
        "SUBMISSION_RD_TOOL_INFO",
        "SYS_NOTICE",
        "WF_INSTANCE",
        "WF_TASK"
    );
    private static final Set<String> ACCOUNT_WHITELIST = Set.of("qydevelop", "enterprise", "approver");
    private static final List<String> TABLE_ORDER = List.of(
        "SYS_USER",
        "SYS_ROLE",
        "SYS_USER_ROLE",
        "SYS_PERMISSION",
        "SYS_ROLE_PERMISSION",
        "ENTERPRISE_PROFILE",
        "SUBMISSION_FORM",
        "SUBMISSION_BASIC_INFO",
        "SUBMISSION_DEVICE_INFO",
        "SUBMISSION_DIGITAL_INFO",
        "SUBMISSION_RD_TOOL_INFO",
        "SUBMISSION_DIGITAL_SYSTEM_OPTION",
        "SUBMISSION_RD_TOOL_OPTION",
        "SUBMISSION_ATTACHMENT",
        "WF_TEMPLATE",
        "WF_TEMPLATE_NODE",
        "WF_INSTANCE",
        "WF_TASK",
        "SYS_NOTICE",
        "AUDIT_LOG",
        "INDUSTRY_PROCESS_BIND",
        "PROCESS_EQUIPMENT_BIND",
        "SURVEY_ENTERPRISE_LIST"
    );

    public static void main(String[] args) throws Exception {
        ExportConfig config = ExportConfig.load();
        List<TableDef> tables;
        try (Connection connection = DriverManager.getConnection(config.url(), config.username(), config.password())) {
            tables = loadTableDefs(connection, config.schema());
            export(connection, config, tables);
        }
        System.out.println("已生成 DM8 初始化 SQL: " + OUTPUT_SQL);
    }

    private static void export(Connection connection, ExportConfig config, List<TableDef> tables) throws Exception {
        Path output = Path.of(OUTPUT_SQL);
        Files.createDirectories(output.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("-- 基于当前 DM8 现库导出的初始化 SQL\n");
            writer.write("-- 导出时间: " + LocalDateTime.now().format(HEADER_TIME_FORMATTER) + "\n");
            writer.write("-- 数据源: " + config.url() + "\n");
            writer.write("-- 用户名: " + config.username() + "\n");
            writer.write("-- 说明: 已排除用户账号、填报业务、通知、审计、流程运行时数据。\n");
            writer.write("-- 排除数据表: " + String.join(", ", DATA_EXCLUDED_TABLES) + "\n\n");

            writer.write("SET SCHEMA " + quoteIdentifier(config.schema()) + ";\n\n");

            for (TableDef table : tables) {
                writeCreateTable(writer, config.schema(), table);
            }
            writer.write("\n");
            for (TableDef table : tables) {
                writeIndexes(writer, config.schema(), table);
            }
            writer.write("\n");
            for (TableDef table : tables) {
                writeForeignKeys(writer, config.schema(), table);
            }
            writer.write("\n");
            writer.write("-- 主数据与配置数据\n\n");
            for (TableDef table : tables) {
                if (DATA_EXCLUDED_TABLES.contains(table.name())) {
                    continue;
                }
                writeTableData(connection, writer, config.schema(), table);
            }
        }
    }

    private static List<TableDef> loadTableDefs(Connection connection, String schema) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Map<String, TableDef> tables = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName == null || tableName.startsWith("##")) {
                    continue;
                }
                tables.put(tableName, new TableDef(tableName));
            }
        }
        for (TableDef table : tables.values()) {
            loadColumns(metaData, schema, table);
            loadPrimaryKey(metaData, schema, table);
            loadIndexes(metaData, schema, table);
            loadForeignKeys(metaData, schema, table);
        }
        Comparator<TableDef> tableOrderComparator = Comparator
            .comparingInt((TableDef t) -> {
                int index = TABLE_ORDER.indexOf(t.name());
                return index >= 0 ? index : Integer.MAX_VALUE;
            })
            .thenComparing(TableDef::name);
        return tables.values().stream()
            .sorted(tableOrderComparator)
            .toList();
    }

    private static void loadColumns(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, schema, table.name(), "%")) {
            while (rs.next()) {
                ColumnDef column = new ColumnDef(
                    rs.getString("COLUMN_NAME"),
                    rs.getInt("DATA_TYPE"),
                    rs.getString("TYPE_NAME"),
                    rs.getInt("COLUMN_SIZE"),
                    rs.getInt("DECIMAL_DIGITS"),
                    rs.getString("COLUMN_DEF"),
                    rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                    "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")),
                    rs.getInt("ORDINAL_POSITION")
                );
                table.columns().add(column);
            }
        }
        table.columns().sort(Comparator.comparingInt(ColumnDef::ordinalPosition));
    }

    private static void loadPrimaryKey(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        Map<Short, String> pkColumns = new TreeMap<>();
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, table.name())) {
            while (rs.next()) {
                table.primaryKeyName(rs.getString("PK_NAME"));
                pkColumns.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        table.primaryKeyColumns().addAll(pkColumns.values());
    }

    private static void loadIndexes(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        Map<String, IndexDef> indexMap = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(null, schema, table.name(), false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                short type = rs.getShort("TYPE");
                if (indexName == null || columnName == null || type == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                IndexDef index = indexMap.computeIfAbsent(indexName, key -> new IndexDef(indexName, unique));
                index.columnsByPosition().put(rs.getShort("ORDINAL_POSITION"), columnName);
            }
        }
        table.indexes().addAll(indexMap.values().stream()
            .filter(index -> !isPrimaryKeyBackedIndex(table, index))
            .toList());
    }

    private static boolean isPrimaryKeyBackedIndex(TableDef table, IndexDef index) {
        if (table.primaryKeyColumns().isEmpty()) {
            return false;
        }
        List<String> indexColumns = index.columnsByPosition().values().stream().toList();
        return Objects.equals(indexColumns, table.primaryKeyColumns());
    }

    private static void loadForeignKeys(DatabaseMetaData metaData, String schema, TableDef table) throws SQLException {
        Map<String, ForeignKeyDef> fkMap = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getImportedKeys(null, schema, table.name())) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String pkTableName = rs.getString("PKTABLE_NAME");
                ForeignKeyDef fk = fkMap.computeIfAbsent(fkName, key -> new ForeignKeyDef(fkName, pkTableName));
                fk.columnsByPosition().put(rs.getShort("KEY_SEQ"), rs.getString("FKCOLUMN_NAME"));
                fk.referencedColumnsByPosition().put(rs.getShort("KEY_SEQ"), rs.getString("PKCOLUMN_NAME"));
            }
        }
        table.foreignKeys().addAll(fkMap.values());
    }

    private static void writeCreateTable(BufferedWriter writer, String schema, TableDef table) throws IOException {
        writer.write("CREATE TABLE " + qualifiedName(schema, table.name()) + " (\n");
        List<String> definitions = new ArrayList<>();
        for (int i = 0; i < table.columns().size(); i++) {
            ColumnDef column = table.columns().get(i);
            StringBuilder definition = new StringBuilder();
            definition.append("    ").append(quoteIdentifier(column.name())).append(" ").append(renderColumnType(column));
            if (column.defaultValue() != null && !column.defaultValue().isBlank() && !column.autoIncrement()) {
                definition.append(" DEFAULT ").append(column.defaultValue());
            }
            if (!column.nullable()) {
                definition.append(" NOT NULL");
            }
            definitions.add(definition.toString());
        }
        if (!table.primaryKeyColumns().isEmpty()) {
            definitions.add("    CONSTRAINT " + quoteIdentifier(stablePrimaryKeyName(table))
                + " PRIMARY KEY (" + joinIdentifiers(table.primaryKeyColumns()) + ")");
        }
        for (int i = 0; i < definitions.size(); i++) {
            writer.write(definitions.get(i));
            if (i < definitions.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write(");\n\n");
    }

    private static void writeIndexes(BufferedWriter writer, String schema, TableDef table) throws IOException {
        Set<String> emittedIndexNames = new LinkedHashSet<>();
        for (IndexDef index : table.indexes()) {
            List<String> columns = index.columnsByPosition().values().stream().toList();
            if (columns.isEmpty()) {
                continue;
            }
            String indexName = stableIndexName(table, index);
            if (!emittedIndexNames.add(indexName)) {
                continue;
            }
            writer.write("CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX "
                + quoteIdentifier(indexName) + " ON "
                + qualifiedName(schema, table.name()) + " (" + joinIdentifiers(columns) + ");\n");
        }
    }

    private static void writeForeignKeys(BufferedWriter writer, String schema, TableDef table) throws IOException {
        for (ForeignKeyDef fk : table.foreignKeys()) {
            List<String> columns = fk.columnsByPosition().values().stream().toList();
            List<String> referencedColumns = fk.referencedColumnsByPosition().values().stream().toList();
            if (columns.isEmpty() || referencedColumns.isEmpty()) {
                continue;
            }
            writer.write("ALTER TABLE " + qualifiedName(schema, table.name())
                + " ADD CONSTRAINT " + quoteIdentifier(stableForeignKeyName(table, fk))
                + " FOREIGN KEY (" + joinIdentifiers(columns) + ") REFERENCES "
                + qualifiedName(schema, fk.referencedTable()) + " (" + joinIdentifiers(referencedColumns) + ");\n");
        }
    }

    private static void writeTableData(Connection connection, BufferedWriter writer, String schema, TableDef table) throws Exception {
        long count = countRows(connection, schema, table.name());
        if (count == 0) {
            return;
        }
        String dataQuery = buildDataQuery(schema, table.name());
        long exportCount = countRowsByQuery(connection, dataQuery);
        if (exportCount == 0) {
            return;
        }
        writer.write("-- 数据表: " + table.name() + "，导出记录数: " + exportCount + "，库内总记录数: " + count + "\n");
        boolean hasIdentityColumn = table.columns().stream().anyMatch(ColumnDef::autoIncrement);
        if (hasIdentityColumn) {
            writer.write("SET IDENTITY_INSERT " + table.name() + " ON;\n");
        }
        try (var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(dataQuery)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                List<String> values = new ArrayList<>();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(quoteIdentifier(metaData.getColumnName(i)));
                    values.add(toSqlLiteral(rs.getObject(i)));
                }
                writer.write("INSERT INTO " + qualifiedName(schema, table.name()) + " ("
                    + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", values) + ");\n");
            }
        }
        if (hasIdentityColumn) {
            writer.write("SET IDENTITY_INSERT " + table.name() + " OFF;\n");
        }
        writer.write("\n");
    }

    private static long countRows(Connection connection, String schema, String tableName) throws SQLException {
        try (var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + qualifiedName(schema, tableName))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long countRowsByQuery(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM (" + sql + ") t")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String buildDataQuery(String schema, String tableName) {
        String qualifiedTableName = qualifiedName(schema, tableName);
        return switch (tableName) {
            case "SYS_USER" -> "SELECT * FROM " + qualifiedTableName
                + " WHERE " + quoteIdentifier("USERNAME") + " IN (" + joinStringLiterals(ACCOUNT_WHITELIST) + ")"
                + buildOrderByForColumns(List.of("ID"));
            case "SYS_USER_ROLE" -> "SELECT ur.* FROM " + qualifiedTableName + " ur"
                + " JOIN " + qualifiedName(schema, "SYS_USER") + " u ON u." + quoteIdentifier("ID") + " = ur." + quoteIdentifier("USER_ID")
                + " WHERE u." + quoteIdentifier("USERNAME") + " IN (" + joinStringLiterals(ACCOUNT_WHITELIST) + ")"
                + " ORDER BY ur." + quoteIdentifier("ID");
            case "ENTERPRISE_PROFILE" -> "SELECT ep.* FROM " + qualifiedTableName + " ep"
                + " WHERE ep." + quoteIdentifier("ID") + " IN ("
                + "SELECT DISTINCT u." + quoteIdentifier("ENTERPRISE_ID")
                + " FROM " + qualifiedName(schema, "SYS_USER") + " u"
                + " WHERE u." + quoteIdentifier("USERNAME") + " IN (" + joinStringLiterals(ACCOUNT_WHITELIST) + ")"
                + " AND u." + quoteIdentifier("ENTERPRISE_ID") + " IS NOT NULL)"
                + " ORDER BY ep." + quoteIdentifier("ID");
            default -> "SELECT * FROM " + qualifiedTableName + buildDefaultOrderBy(tableName);
        };
    }

    private static String buildOrderBy(TableDef table) {
        if (!table.primaryKeyColumns().isEmpty()) {
            return " ORDER BY " + joinIdentifiers(table.primaryKeyColumns());
        }
        ColumnDef idColumn = table.columns().stream()
            .filter(column -> "ID".equalsIgnoreCase(column.name()))
            .findFirst()
            .orElse(null);
        return idColumn == null ? "" : " ORDER BY " + quoteIdentifier(idColumn.name());
    }

    private static String buildDefaultOrderBy(String tableName) {
        return buildOrderByForColumns(switch (tableName) {
            case "SYS_ROLE_PERMISSION" -> List.of("ID");
            default -> List.of("ID");
        });
    }

    private static String buildOrderByForColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return "";
        }
        return " ORDER BY " + columns.stream().map(Dm8InitSqlExportTool::quoteIdentifier).collect(Collectors.joining(", "));
    }

    private static String joinStringLiterals(Collection<String> values) {
        return values.stream()
            .map(value -> "'" + escapeSql(value) + "'")
            .collect(Collectors.joining(", "));
    }

    private static String stablePrimaryKeyName(TableDef table) {
        return normalizedIdentifier("PK_" + table.name());
    }

    private static String stableIndexName(TableDef table, IndexDef index) {
        String prefix = index.unique() ? "UK_" : "IDX_";
        String columnSuffix = String.join("_", index.columnsByPosition().values());
        return normalizedIdentifier(prefix + table.name() + "_" + columnSuffix);
    }

    private static String stableForeignKeyName(TableDef table, ForeignKeyDef fk) {
        String columnSuffix = String.join("_", fk.columnsByPosition().values());
        return normalizedIdentifier("FK_" + table.name() + "_" + columnSuffix);
    }

    private static String normalizedIdentifier(String raw) {
        String sanitized = raw.toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9_]", "_")
            .replaceAll("_+", "_");
        if (sanitized.length() <= 128) {
            return sanitized;
        }
        String hash = Integer.toHexString(sanitized.hashCode()).toUpperCase(Locale.ROOT);
        int prefixLength = 128 - hash.length() - 1;
        return sanitized.substring(0, Math.max(prefixLength, 1)) + "_" + hash;
    }

    private static String renderColumnType(ColumnDef column) {
        if (column.autoIncrement()) {
            return column.typeName() + " IDENTITY(1,1)";
        }
        int dataType = column.dataType();
        if (dataType == Types.VARCHAR || dataType == Types.CHAR || dataType == Types.NVARCHAR || dataType == Types.NCHAR) {
            return column.typeName() + "(" + column.columnSize() + ")";
        }
        if (dataType == Types.DECIMAL || dataType == Types.NUMERIC) {
            return column.typeName() + "(" + column.columnSize() + "," + column.decimalDigits() + ")";
        }
        if (dataType == Types.TIMESTAMP || dataType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return column.decimalDigits() > 0 ? column.typeName() + "(" + column.decimalDigits() + ")" : column.typeName();
        }
        if (column.columnSize() > 0 && needsLength(column.typeName(), dataType)) {
            return column.typeName() + "(" + column.columnSize() + ")";
        }
        return column.typeName();
    }

    private static boolean needsLength(String typeName, int dataType) {
        return dataType == Types.BINARY
            || dataType == Types.VARBINARY
            || "VARCHAR2".equalsIgnoreCase(typeName)
            || "NVARCHAR2".equalsIgnoreCase(typeName);
    }

    private static String toSqlLiteral(Object value) throws SQLException {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String stringValue) {
            return "'" + escapeSql(stringValue) + "'";
        }
        if (value instanceof Character character) {
            return "'" + escapeSql(character.toString()) + "'";
        }
        if (value instanceof Timestamp timestamp) {
            return "TO_TIMESTAMP('" + TIMESTAMP_FORMAT.format(timestamp) + "', 'YYYY-MM-DD HH24:MI:SS.FF6')";
        }
        if (value instanceof Date date) {
            return "TO_DATE('" + DATE_FORMAT.format(date) + "', 'YYYY-MM-DD')";
        }
        if (value instanceof java.sql.Clob clob) {
            return "'" + escapeSql(clob.getSubString(1, (int) clob.length())) + "'";
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.toPlainString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "'" + escapeSql(String.valueOf(value)) + "'";
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private static String qualifiedName(String schema, String name) {
        return quoteIdentifier(schema) + "." + quoteIdentifier(name);
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    private static String joinIdentifiers(Collection<String> identifiers) {
        return identifiers.stream().map(Dm8InitSqlExportTool::quoteIdentifier).collect(Collectors.joining(", "));
    }

    private record ExportConfig(String url, String username, String password, String schema) {
        private static ExportConfig load() {
            Properties properties = loadYamlProperties();
            String url = resolvePlaceholder(properties.getProperty("spring.datasource.url"));
            String username = resolvePlaceholder(properties.getProperty("spring.datasource.username"));
            String password = resolvePlaceholder(properties.getProperty("spring.datasource.password"));
            if (url == null || username == null || password == null) {
                throw new IllegalStateException("无法从 application.yml 读取达梦数据源配置");
            }
            return new ExportConfig(url, username, password, username.toUpperCase(Locale.ROOT));
        }

        private static Properties loadYamlProperties() {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new FileSystemResource(APPLICATION_YML));
            Properties properties = factory.getObject();
            if (properties == null) {
                throw new IllegalStateException("读取 application.yml 失败");
            }
            return properties;
        }

        private static String resolvePlaceholder(String raw) {
            if (raw == null) {
                return null;
            }
            return PLACEHOLDER_HELPER.replacePlaceholders(raw, System::getenv);
        }
    }

    private static final class TableDef {
        private final String name;
        private final List<ColumnDef> columns = new ArrayList<>();
        private final List<String> primaryKeyColumns = new ArrayList<>();
        private final List<IndexDef> indexes = new ArrayList<>();
        private final List<ForeignKeyDef> foreignKeys = new ArrayList<>();
        private String primaryKeyName;

        private TableDef(String name) {
            this.name = name;
        }

        public String name() {
            return name;
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

        public List<ForeignKeyDef> foreignKeys() {
            return foreignKeys;
        }

        public String primaryKeyName() {
            return primaryKeyName;
        }

        public void primaryKeyName(String primaryKeyName) {
            this.primaryKeyName = primaryKeyName;
        }
    }

    private record ColumnDef(
        String name,
        int dataType,
        String typeName,
        int columnSize,
        int decimalDigits,
        String defaultValue,
        boolean nullable,
        boolean autoIncrement,
        int ordinalPosition
    ) {
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

    private static final class ForeignKeyDef {
        private final String name;
        private final String referencedTable;
        private final Map<Short, String> columnsByPosition = new TreeMap<>();
        private final Map<Short, String> referencedColumnsByPosition = new TreeMap<>();

        private ForeignKeyDef(String name, String referencedTable) {
            this.name = name;
            this.referencedTable = referencedTable;
        }

        public String name() {
            return name;
        }

        public String referencedTable() {
            return referencedTable;
        }

        public Map<Short, String> columnsByPosition() {
            return columnsByPosition;
        }

        public Map<Short, String> referencedColumnsByPosition() {
            return referencedColumnsByPosition;
        }
    }
}
