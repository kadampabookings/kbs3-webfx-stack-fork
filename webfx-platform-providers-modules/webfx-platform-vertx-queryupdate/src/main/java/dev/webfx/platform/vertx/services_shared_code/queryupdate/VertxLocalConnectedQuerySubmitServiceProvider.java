package dev.webfx.platform.vertx.services_shared_code.queryupdate;

import dev.webfx.platform.server.services.submitlistener.SubmitListenerService;
import dev.webfx.platform.shared.services.datasource.ConnectionDetails;
import dev.webfx.platform.shared.services.datasource.DBMS;
import dev.webfx.platform.shared.services.datasource.LocalDataSource;
import dev.webfx.platform.shared.services.datasource.jdbc.JdbcDriverInfo;
import dev.webfx.platform.shared.services.log.Logger;
import dev.webfx.platform.shared.services.query.QueryArgument;
import dev.webfx.platform.shared.services.query.QueryResult;
import dev.webfx.platform.shared.services.query.QueryResultBuilder;
import dev.webfx.platform.shared.services.query.spi.QueryServiceProvider;
import dev.webfx.platform.shared.services.submit.GeneratedKeyBatchIndex;
import dev.webfx.platform.shared.services.submit.SubmitArgument;
import dev.webfx.platform.shared.services.submit.SubmitResult;
import dev.webfx.platform.shared.services.submit.spi.SubmitServiceProvider;
import dev.webfx.platform.shared.util.Arrays;
import dev.webfx.platform.shared.util.async.Batch;
import dev.webfx.platform.shared.util.async.Future;
import dev.webfx.platform.shared.util.tuples.Unit;
import dev.webfx.platform.vertx.services_shared_code.instance.VertxInstance;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Bruno Salmon
 */
public final class VertxLocalConnectedQuerySubmitServiceProvider implements QueryServiceProvider, SubmitServiceProvider {

    private final Pool pool;

    public VertxLocalConnectedQuerySubmitServiceProvider(LocalDataSource localDataSource) {
        // Generating the Vertx Sql config from the connection details
        ConnectionDetails connectionDetails = localDataSource.getLocalConnectionDetails();
        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(20);
        Vertx vertx = VertxInstance.getVertx();
        DBMS dbms = localDataSource.getDBMS();
        switch (dbms) {
            case POSTGRES: {
                PgConnectOptions connectOptions = new PgConnectOptions()
                        .setHost(connectionDetails.getHost())
                        .setPort(connectionDetails.getPort())
                        .setDatabase(connectionDetails.getDatabaseName())
                        .setUser(connectionDetails.getUsername())
                        .setPassword(connectionDetails.getPassword());
                pool = PgPool.pool(vertx, connectOptions, poolOptions);
                break;
            }
            case MYSQL: // TODO implement MySQL
            default: {
                JdbcDriverInfo jdbcDriverInfo = JdbcDriverInfo.from(dbms);
                JDBCConnectOptions connectOptions = new JDBCConnectOptions()
                        .setJdbcUrl(jdbcDriverInfo.getUrlOrGenerateJdbcUrl(connectionDetails))
                        .setDatabase(connectionDetails.getDatabaseName()) // Necessary?
                        .setUser(connectionDetails.getUsername())
                        .setPassword(connectionDetails.getPassword());
                // Note: Works only with the Agroal connection pool
                pool = JDBCPool.pool(vertx, connectOptions, poolOptions);
            }
        }
    }

    @Override
    public Future<QueryResult> executeQuery(QueryArgument queryArgument) {
        return connectAndExecute((connection, future) -> executeSingleQueryOnConnection(queryArgument, connection, future));
    }

    @Override
    public Future<SubmitResult> executeSubmit(SubmitArgument submitArgument) {
        return connectAndExecuteInTransaction((connection, transaction, future) -> executeSubmitOnConnection(submitArgument, connection, transaction, false, future));
    }

    @Override
    public Future<Batch<SubmitResult>> executeSubmitBatch(Batch<SubmitArgument> batch) {
        // Singular batch optimization: executing the single sql order in autocommit mode
        Future<Batch<SubmitResult>> singularBatchFuture = batch.executeIfSingularBatch(SubmitResult[]::new, this::executeSubmit);
        if (singularBatchFuture != null)
            return singularBatchFuture;

        // Now handling real batch with several arguments -> no autocommit with explicit commit() or rollback() handling
        return connectAndExecuteInTransaction((connection, transaction, batchFuture) -> executeUpdateBatchOnConnection(batch, connection, transaction, batchFuture));
    }


    // ==================================== PRIVATE IMPLEMENTATION PART  ===============================================

    private <T> Future<T> connectAndExecute(BiConsumer<SqlConnection, Future<T>> executor) {
        Future<T> future = Future.future();
        pool.getConnection()
                .onFailure(cause -> {
                    Logger.log(cause);
                    future.fail(cause);
                })
                .onSuccess(connection -> executor.accept(connection, future)); // Note: this is the responsibility of the executor to close the connection
        return future;
    }

    private interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    private <T> Future<T> connectAndExecuteInTransaction(TriConsumer<SqlConnection, Transaction, Future<T>> executor) {
        return connectAndExecute((connection, future) ->
                connection.begin()
                        .onFailure(future::fail)
                        .onSuccess(transaction -> executor.accept(connection, transaction, future))
        );
    }

    private void closeConnection(SqlConnection connection) {
        connection.close();
        //Logger.log("open = " + --open);
    }

    private static void onSuccessfulSubmit(SubmitArgument submitArgument) {
        SubmitListenerService.fireSuccessfulSubmit(submitArgument);
    }

    private static void onSuccessfulSubmitBatch(Batch<SubmitArgument> batch) {
        SubmitListenerService.fireSuccessfulSubmit(batch.getArray());
    }

    private void executeSingleQueryOnConnection(QueryArgument queryArgument, SqlConnection connection, Future<QueryResult> future) {
        // Logger.log("Single query with " + queryArgument);
        // long t0 = System.currentTimeMillis();
        executeQueryOnConnection(queryArgument.getStatement(), queryArgument.getParameters(), connection, ar -> {
            if (ar.failed()) // Sql error
                future.fail(ar.cause());
            else { // Sql succeeded
                // Transforming the result set into columnNames and values arrays
                RowSet<Row> resultSet = ar.result();
                int columnCount = resultSet.columnsNames().size();
                int rowCount = resultSet.size();
                QueryResultBuilder rsb = QueryResultBuilder.create(rowCount, columnCount);
                // deactivated column names serialization - rsb.setColumnNames(resultSet.getColumnNames().toArray(new String[columnCount]));
                int rowIndex = 0;
                for (Row row : resultSet) {
                    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                        Object value = row.getValue(columnIndex);
                        if (value instanceof LocalDate)
                            value = ((LocalDate) value).atStartOfDay().toInstant(ZoneOffset.UTC);
                        else if (value instanceof LocalDateTime)
                            value = ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
                        rsb.setValue(rowIndex, columnIndex, value);
                    }
                    rowIndex++;
                }
                // Logger.log("Sql executed in " + (System.currentTimeMillis() - t0) + " ms: " + queryArgument);
                // Building and returning the final QueryResult
                future.complete(rsb.build());
            }
            // Closing the connection, so it can go back to the pool
            closeConnection(connection);
        });
    }

    private void executeQueryOnConnection(String queryString, Object[] parameters, SqlConnection connection, Handler<AsyncResult<RowSet<Row>>> resultHandler) {
        // Calling query() or preparedQuery() depending on if parameters are provided or not
        if (Arrays.isEmpty(parameters))
            connection.query(queryString)
                    .execute(resultHandler);
        else {
            for (int i = 0; i < parameters.length; i++)
                if (parameters[i] instanceof Instant)
                    parameters[i] = LocalDateTime.ofInstant((Instant) parameters[i], ZoneOffset.UTC);
            connection.preparedQuery(queryString)
                    .execute(Tuple.from(parameters), resultHandler);
        }
    }

    private Future<SubmitResult> executeSubmitOnConnection(SubmitArgument submitArgument, SqlConnection connection, Transaction transaction, boolean batch, Future<SubmitResult> future) {
        //Logger.log(submitArgument);
        executeQueryOnConnection(submitArgument.getStatement(), submitArgument.getParameters(), connection, res -> {
            if (res.failed()) { // Sql error
                // Unless from batch, closing the connection now, so it can go back to the pool
                if (!batch)
                    closeConnection(connection);
                future.fail(res.cause());
            } else { // Sql succeeded
                RowSet<Row> result = res.result();
                Object[] generatedKeys = null;
                if (submitArgument.returnGeneratedKeys() || submitArgument.getStatement().contains(" returning ")) {
                    generatedKeys = new Object[result.size()];
                    int rowIndex = 0;
                    for (Row row : result)
                        generatedKeys[rowIndex++] = row.getValue(0);
                }
                SubmitResult submitResult = new SubmitResult(result.rowCount(), generatedKeys);
                if (batch)
                    future.complete(submitResult);
                else {
                    transaction.commit(ar -> {
                        if (ar.failed())
                            future.fail(ar.cause());
                        else
                            future.complete(submitResult);
                        closeConnection(connection);
                        onSuccessfulSubmit(submitArgument);
                    });
                }
            }
        });
        return future;
    }

    private void executeUpdateBatchOnConnection(Batch<SubmitArgument> batch, SqlConnection connection, Transaction transaction, Future<Batch<SubmitResult>> batchFuture) {
        List<Object> batchIndexGeneratedKeys = new ArrayList<>(Collections.nCopies(batch.getArray().length, null));
        Unit<Integer> batchIndex = new Unit<>(0);
        batch.executeSerial(batchFuture, SubmitResult[]::new, updateArgument -> {
            Future<SubmitResult> statementFuture = Future.future();
            // Replacing GeneratedKeyBatchIndex parameters with their actual generated keys
            Object[] parameters = updateArgument.getParameters();
            for (int i = 0, length = Arrays.length(parameters); i < length; i++) {
                Object value = parameters[i];
                if (value instanceof GeneratedKeyBatchIndex)
                    parameters[i] = batchIndexGeneratedKeys.get(((GeneratedKeyBatchIndex) value).getBatchIndex());
            }
            executeSubmitOnConnection(updateArgument, connection, transaction, true, Future.future()).setHandler(ar -> {
                if (ar.failed()) { // Sql error
                    statementFuture.fail(ar.cause());
                    transaction.rollback(event -> closeConnection(connection));
                } else { // Sql succeeded
                    SubmitResult submitResult = ar.result();
                    Object[] generatedKeys = submitResult.getGeneratedKeys();
                    if (!Arrays.isEmpty(generatedKeys))
                        batchIndexGeneratedKeys.set(batchIndex.get(), generatedKeys[0]);
                    batchIndex.set(batchIndex.get() + 1);
                    if (batchIndex.get() < batch.getArray().length)
                        statementFuture.complete(submitResult);
                    else
                        transaction.commit(ar2 -> {
                            if (ar2.failed())
                                statementFuture.fail(ar2.cause());
                            else
                                statementFuture.complete(submitResult);
                            closeConnection(connection);
                            onSuccessfulSubmitBatch(batch);
                        });
                }
            });
            return statementFuture;
        });
    }
}
