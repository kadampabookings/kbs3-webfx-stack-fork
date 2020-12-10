package webfx.framework.shared.orm.dql.sqlcompiler.sql;

import webfx.framework.shared.orm.expression.Expression;
import webfx.framework.shared.orm.dql.sqlcompiler.lci.CompilerDomainModelReader;
import webfx.framework.shared.orm.dql.sqlcompiler.sql.dbms.DbmsSqlSyntax;
import webfx.framework.shared.orm.dql.sqlcompiler.sql.dbms.HsqlSyntax;
import webfx.framework.shared.orm.dql.sqlcompiler.terms.Options;
import webfx.framework.shared.orm.dql.sqlcompiler.mapping.QueryColumnToEntityFieldMapping;
import webfx.framework.shared.orm.dql.sqlcompiler.mapping.QueryRowToEntityMapping;
import webfx.platform.shared.util.Numbers;
import webfx.platform.shared.util.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Bruno Salmon
 */
public final class SqlBuild {
    /**
     * Final fields built by sql compilation in order to generate a SqlCompiled object **
     */
    private String sql;
    private final ArrayList<String> parameterNames = new ArrayList<>();
    private boolean isQuery;
    private String[] autoGeneratedKeyColumnNames;
    private String countSql; // select count(*) ... used to return the total row numbers when the query is truncated by the limit clause

    private final Object selectDomainClass;
    private final ArrayList<QueryColumnToEntityFieldMapping> columnMappings = new ArrayList<>(); // first one = id column

    private Expression sqlUncompilableCondition;
    private boolean cacheable = true;

    /**
     * Temporary fields used during sql compilation **
     */
    private final SqlBuild parent;
    private final String tableName;
    private final String tableAlias;
    private Object compilingClass;
    private String compilingTableAlias;
    private QueryColumnToEntityFieldMapping leftJoinMapping;
    private final DbmsSqlSyntax dbmsSyntax;

    private boolean distinct;

    private final HashMap<SqlClause, StringBuilder> sqlClauseBuilders = new HashMap<>();
    private final HashMap<String /* table alias */, Map<Join, Join> /* joins */ > joins = new HashMap<>();

    private final HashMap<String, String> tableAliases = new HashMap<>();   // tableAlias => tableName
    private HashMap<String, String> logicalAliases = null; // logicalAlias => sqlAlias
    private final List<String> orderedAliases = new ArrayList<>();
    private final HashMap<String, QueryColumnToEntityFieldMapping> fullColumnNameToColumnMappings = new HashMap<>(); // tableAlias.columnName => columnMapping
    //private int fromTablesCount;

    public SqlBuild(SqlBuild parent, Object selectDomainClass, String tableAlias, SqlClause clause, DbmsSqlSyntax dbmsSyntax, CompilerDomainModelReader modelReader) {
        this.parent = parent;
        this.compilingClass = this.selectDomainClass = selectDomainClass;
        tableName = modelReader.getDomainClassSqlTableName(selectDomainClass);
        this.compilingTableAlias = this.tableAlias = getNewTableAlias(tableName, tableAlias, false);
        this.dbmsSyntax = dbmsSyntax;
        prepareAppend(clause, ""); // just for marking the clause
    }

    public Object getSelectDomainClass() {
        return selectDomainClass;
    }

    public DbmsSqlSyntax getDbmsSyntax() {
        return dbmsSyntax;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public int getParameterCount() {
        return parameterNames.size() + (parent == null ? 0 : parent.getParameterCount());
    }

    public void setAutoGeneratedKeyColumnNames(String[] autoGeneratedKeyColumnNames) {
        this.autoGeneratedKeyColumnNames = autoGeneratedKeyColumnNames;
        if (autoGeneratedKeyColumnNames != null && dbmsSyntax == HsqlSyntax.get()) // HSQL error if not uppercase
            for (int i = 0; i < autoGeneratedKeyColumnNames.length; i++)
                autoGeneratedKeyColumnNames[i] = autoGeneratedKeyColumnNames[i].toUpperCase();
    }

    public Expression getSqlUncompilableCondition() {
        return sqlUncompilableCondition;
    }

    public void setSqlUncompilableCondition(Expression sqlUncompilableCondition) {
        this.sqlUncompilableCondition = sqlUncompilableCondition;
    }

    public void setCacheable(boolean cacheable) {
        this.cacheable = cacheable;
        if (!cacheable && parent != null)
            parent.setCacheable(false);
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public SqlCompiled toSqlCompiled() {
        int n = columnMappings.size();
        QueryRowToEntityMapping queryMapping = null;
        if (n > 0) {
            QueryColumnToEntityFieldMapping[] nonIdColumnMappings = new QueryColumnToEntityFieldMapping[n - 1];
            for (int i = 1; i < n; i++)
                nonIdColumnMappings[i - 1] = columnMappings.get(i);
            queryMapping = new QueryRowToEntityMapping(0, selectDomainClass, nonIdColumnMappings);
        }
        return new SqlCompiled(toSql(), toCountSql(), parameterNames, isQuery, autoGeneratedKeyColumnNames, queryMapping, sqlUncompilableCondition, cacheable);
    }

    public String toSql() {
        if (sql == null) {
            StringBuilder sb = new StringBuilder();
            StringBuilder select = getClauseBuilder(SqlClause.SELECT);
            StringBuilder insert = getClauseBuilder(SqlClause.INSERT);
            isQuery = select != null;
            if (isQuery)
                sb.append("select ").append(_if(distinct, "distinct ")).append(select).append(" from ");
            else if (getClauseBuilder(SqlClause.UPDATE) != null)
                sb.append("update ");
            else if (insert != null)
                sb.append("insert into ");
            else if (getClauseBuilder(SqlClause.DELETE) != null)
                sb.append("delete ").append(_if(dbmsSyntax.repeatTableAliasAfterDelete(), tableAlias)).append(" from ");
            //sb.append(_if(fromTablesCount > 1, "(")); // select * from (t1, t2) join ... (marche pas avec postgres)
            boolean first = true;
            for (String tableAlias : orderedAliases) {
                if (!isJoinTableAlias(tableAlias)) {
                    String tableName = tableAliases.get(tableAlias);
                    if (!first)
                        sb.append(", ");
                    sb.append(dbmsSyntax.quoteTableIfReserved(tableName)); // tableName
                    if (insert == null) // no alias allowed in insert sql statement
                        sb.append(" as ").append(tableAlias); // may need " as " instead of ' ' for some dbms
                    first = false;
                }
                Join.appendJoins(joins.get(tableAlias), sb);
            }
            sb//.append(_if(fromTablesCount > 1, ") "))
                    .append(_if(" set ", getClauseBuilder(SqlClause.UPDATE), "", sb))
                    .append(_if(" (", insert, ")", sb))
                    .append(_if(" values (", getClauseBuilder(SqlClause.VALUES), ")", sb))
                    .append(_if(" where ", getClauseBuilder(SqlClause.WHERE), "", sb))
                    .append(_if(" group by ", getClauseBuilder(SqlClause.GROUP_BY), "", sb))
                    .append(_if(" having ", getClauseBuilder(SqlClause.HAVING), "", sb))
                    .append(_if(" order by ", getClauseBuilder(SqlClause.ORDER_BY), "", sb))
                    .append(_if(" limit ", getClauseBuilder(SqlClause.LIMIT), "", sb))
                    .append(_if(" returning ", getClauseBuilder(SqlClause.RETURNING), "", sb));
            sql = sb.toString();
        }
        return sql;
    }

    public String toCountSql() {
        if (countSql == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("select count(*) from ");
            boolean first = true;
            for (String tableAlias : orderedAliases) {
                if (!isJoinTableAlias(tableAlias)) {
                    String tableName = tableAliases.get(tableAlias);
                    if (!first)
                        sb.append(", ");
                    sb.append(dbmsSyntax.quoteTableIfReserved(tableName)); // tableName
                    sb.append(" as ").append(tableAlias); // may need " as " instead of ' ' for some dbms
                    first = false;
                }
                Join.appendJoins(joins.get(tableAlias), sb);
            }
            sb//.append(_if(fromTablesCount > 1, ") "))
                    .append(_if(" where ", getClauseBuilder(SqlClause.WHERE), "", sb))
                    .append(_if(" group by ", getClauseBuilder(SqlClause.GROUP_BY), "", sb))
                    .append(_if(" having ", getClauseBuilder(SqlClause.HAVING), "", sb));
            countSql = sb.toString();
        }
        return countSql;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public Object getCompilingClass() {
        return compilingClass;
    }

    public void setCompilingClass(Object compilingClass) {
        this.compilingClass = compilingClass;
    }

    public String getCompilingTableAlias() {
        return compilingTableAlias;
    }

    public void setCompilingTableAlias(String compilingTableAlias) {
        this.compilingTableAlias = compilingTableAlias;
    }

    public String getClassAlias(Object domainClass, CompilerDomainModelReader modelReader) {
        if  (domainClass == compilingClass)
            return compilingTableAlias;
        String tableName = modelReader.getDomainClassSqlTableName(domainClass);
        for (Map.Entry<String, String> entry : tableAliases.entrySet()) {
            if (tableName.equals(entry.getValue()))
                return entry.getKey();
        }
        if (parent != null)
            return parent.getClassAlias(domainClass, modelReader);
        return null;
    }

    public QueryColumnToEntityFieldMapping getLeftJoinMapping() {
        return leftJoinMapping;
    }

    public void setLeftJoinMapping(QueryColumnToEntityFieldMapping leftJoinMapping) {
        this.leftJoinMapping = leftJoinMapping;
    }

    private StringBuilder getClauseBuilder(SqlClause clause) {
        return sqlClauseBuilders.get(clause);
    }

    public int evaluateLimit() {
        StringBuilder limit = getClauseBuilder(SqlClause.LIMIT);
        return Strings.isEmpty(limit) ? -1 : Numbers.intValue(limit);
    }


    /* Building methods */

    public StringBuilder prepareAppend(Options o) {
        return prepareAppend(o.clause, o.separator);
    }

    public StringBuilder prepareAppend(SqlClause clause, String separator) {
        StringBuilder clauseBuilder = sqlClauseBuilders.get(clause);
        if (clauseBuilder == null)
            sqlClauseBuilders.put(clause, clauseBuilder = new StringBuilder());
        if (Strings.isNotEmpty(separator) && Strings.isNotEmpty(clauseBuilder) && !endsWith(clauseBuilder, separator) && !endsWith(clauseBuilder, "(") && !endsWith(clauseBuilder, "["))
            clauseBuilder.append(separator);
        return clauseBuilder;
    }

    private static boolean endsWith(StringBuilder sb, String s) {
        int sbLength = sb.length();
        int sLength = s.length();
        if (sLength == 0 || sbLength < sLength)
            return false;
        for (int i = 0; i < sLength; i++)
            if (sb.charAt(i + sbLength - sLength) != s.charAt(i))
                return false;
        return true;
    }

    private String getNewTableAlias(String tableName, String tableAlias, boolean join) {
        if (tableAlias == null) {
            char c = join ? 'j' : 't';
            StringBuilder sb = new StringBuilder();
            for (SqlBuild b = this; b != null; b = b.parent)
                sb.append(c);
            sb.append(tableAliases.size() + 1);
            tableAlias = sb.toString();
        }
        tableAliases.put(tableAlias, tableName);
        orderedAliases.add(tableAlias);
        return tableAlias;
    }

    private boolean isJoinTableAlias(String tableAlias) {
        return tableAlias.charAt(0) == 'j';
    }

    private SqlBuild getLogicalAliasBuild(String logicalAlias) {
        if (logicalAlias == null || logicalAlias.equals(tableAlias))
            return this;
        if (logicalAliases != null && logicalAliases.containsKey(logicalAlias))
            return this;
        if (parent != null)
            return parent.getLogicalAliasBuild(logicalAlias);
        return null;
    }

    public String getSqlAlias(String logicalAlias) {
        return getSqlAlias(logicalAlias, getLogicalAliasBuild(logicalAlias));
    }

    private String getSqlAlias(String logicalAlias, SqlBuild logicalAliasBuild) {
        String sqlAlias = null;
        if (logicalAliasBuild != null && logicalAliasBuild.logicalAliases != null)
            sqlAlias = logicalAliasBuild.logicalAliases.get(logicalAlias);
        return sqlAlias != null ? sqlAlias : logicalAlias;
    }

    private void recordLogicalAlias(String logicalAlias, String sqlAlias) {
        if (logicalAliases == null)
            logicalAliases = new HashMap<>();
        logicalAliases.put(logicalAlias, sqlAlias);
    }

    public QueryColumnToEntityFieldMapping addColumnInClause(String tableAlias, String columnName, Object fieldId, Object foreignFieldClassId, SqlClause clause, String separator, boolean grouped, boolean isBoolean, boolean generateQueryMapping) {
        if (clause == SqlClause.INSERT || clause == SqlClause.VALUES || clause == SqlClause.UPDATE /* Postgres doesn't like alias in set clause */)
            tableAlias = null;
        else
            tableAlias = getSqlAlias(tableAlias);
        String fullColumnName = dbmsSyntax.quoteColumnIfReserved(columnName);
        if (tableAlias != null)
            fullColumnName = tableAlias + '.' + fullColumnName;
        QueryColumnToEntityFieldMapping queryColumnToEntityFieldMapping = null;
        if (clause == SqlClause.SELECT) {
            queryColumnToEntityFieldMapping = fullColumnNameToColumnMappings.get(fullColumnName);
            if (queryColumnToEntityFieldMapping != null && generateQueryMapping) // doesn't append column except when not generateQueryMapping (ex: function call)
                fullColumnName = null;
            else if (generateQueryMapping) { // queryColumnToEntityFieldMapping is necessary null in this case
                fullColumnNameToColumnMappings.put(fullColumnName, queryColumnToEntityFieldMapping = new QueryColumnToEntityFieldMapping(fullColumnNameToColumnMappings.size(), fieldId, foreignFieldClassId, leftJoinMapping));
                columnMappings.add(queryColumnToEntityFieldMapping);
            }
        }
        if (fullColumnName != null) {
            if (grouped && (clause == SqlClause.SELECT || clause == SqlClause.ORDER_BY))
                fullColumnName = (isBoolean ? "first(" : "min(") + fullColumnName + ")"; // min is much faster (native) than first (written in sql) but min doesn't work for boolean in postgres
            prepareAppend(clause, separator).append(fullColumnName);
        }
        return queryColumnToEntityFieldMapping;
    }

    public String addJoinCondition(String table1Alias, String column1Name, String table2Alias, String table2Name, String column2Name, boolean leftOuter) {
        SqlBuild logicalAliasBuild = getLogicalAliasBuild(table1Alias);
        if (logicalAliasBuild == null)
            logicalAliasBuild = this;
        return logicalAliasBuild.addJoinCondition2(table1Alias, column1Name, table2Alias, table2Name, column2Name, leftOuter);
    }

    private String addJoinCondition2(String table1Alias, String column1Name, String table2Alias, String table2Name, String column2Name, boolean leftOuter) {
        table1Alias = getSqlAlias(table1Alias, this);
        Map<Join, Join> table1Joins = joins.get(table1Alias);
        if (table1Joins == null)
            joins.put(table1Alias, table1Joins =  new HashMap<>());
        Join join = new Join(table1Alias, column1Name, table2Name, column2Name, null, leftOuter);
        Join existingJoin = table1Joins.get(join); // fetching the map to see if the join already exists (see Join.equals())
        if (existingJoin != null) {
            join = existingJoin;
            join.leftOuter &= leftOuter;
        } else {
            join.table2Alias = getNewTableAlias(table2Name, null, true);
            table1Joins.put(join, join);
        }
        if (table2Alias == null)
            recordLogicalAlias(join.table2Alias, join.table2Alias);
        else if (!table2Alias.equals(join.table2Alias)) {
            recordLogicalAlias(table2Alias, join.table2Alias);
            return table2Alias;
        }
        return join.table2Alias;
    }

    private static final class Join {
        // Join identifying fields (to include in equals and hash)
        final String table1Alias;
        final String column1Name;
        final String table2Name;
        final String column2Name;

        // Join attributes (not to include in equals and hash)
        String table2Alias;
        boolean leftOuter;

        private Join(String table1Alias, String column1Name, String table2Name, String column2Name, String table2Alias, boolean leftOuter) {
            this.table1Alias = table1Alias;
            this.column1Name = column1Name;
            this.table2Name = table2Name;
            this.column2Name = column2Name;
            this.table2Alias = table2Alias;
            this.leftOuter = leftOuter;
        }

        void appendTo(StringBuilder sb) {
            if (leftOuter)
                sb.append(" left");
            sb.append(" join ").append(table2Name).append(' ').append(table2Alias);
            if (column1Name.equals(column2Name)) // 'using' syntax when column names are identical
                sb.append(" using ").append(column1Name);
            else { // 'on' syntax
                sb.append(" on ").append(table2Alias).append('.').append(column2Name).append('=');
                if (table1Alias != null)
                    sb.append(table1Alias).append('.');
                sb.append(column1Name);
            }
        }

        static void appendJoins(Map<Join, Join> joinMap, StringBuilder sb) {
            if (joinMap != null)
                for (Join join : joinMap.keySet())
                    join.appendTo(sb);
        }

        static void appendNotYetAppendJoins(Map<Join, Join> joinMap, StringBuilder sb) {
            if (joinMap != null)
                for (Join join : joinMap.keySet()) {
                    StringBuilder sb2 = new StringBuilder();
                    join.appendTo(sb2);
                    String s2 = sb2.toString();
                    if (!sb.toString().contains(s2))
                        sb.append(s2);
                }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Join join = (Join) o;

            if (!column1Name.equals(join.column1Name)) return false;
            if (!column2Name.equals(join.column2Name)) return false;
            if (!table1Alias.equals(join.table1Alias)) return false;
            return table2Name.equals(join.table2Name);
        }

        @Override
        public int hashCode() {
            int result = table1Alias.hashCode();
            result = 31 * result + column1Name.hashCode();
            result = 31 * result + table2Name.hashCode();
            result = 31 * result + column2Name.hashCode();
            return result;
        }
    }


    // Some Strings static method helpers

    private static String _if(boolean condition, String s) {
        return condition && s != null ? s : "";
    }

    private static String _if(String before, StringBuilder s, String after, StringBuilder sb) {
        if (Strings.isNotEmpty(s)) {
            if (before != null)
                sb.append(before);
            sb.append(s);
            if (after != null)
                sb.append(after);
        }
        return "";
    }
}
