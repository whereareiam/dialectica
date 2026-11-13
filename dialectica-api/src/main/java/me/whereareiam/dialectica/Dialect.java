package me.whereareiam.dialectica;

import me.whereareiam.dialectica.dialect.SchemaRenderer;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * A database Dialectica can write schemas to and read entities from: how it names things, which
 * SQL it speaks and how it stores values.
 * <p>
 * Dialects are discovered with {@link ServiceLoader}. A dialect module lists its implementation
 * in {@code META-INF/services/me.whereareiam.dialectica.Dialect}, and {@link #of(DatabaseMetaData)}
 * picks the one that accepts an open connection:
 * <pre>{@code
 * Dialect dialect = Dialect.of(connection.getMetaData());
 * List<String> statements = dialect.schemaRenderer().render(new CreateTable(accounts));
 * }</pre>
 * An implementation needs a public constructor without parameters.
 */
public interface Dialect {
	/**
	 * Returns the dialect's name, such as {@code PostgreSQL}, for messages.
	 *
	 * @return the dialect's name
	 */
	@NotNull String name();

	/**
	 * Tells whether this dialect speaks for the database behind a connection, usually by its
	 * {@link DatabaseMetaData#getDatabaseProductName() product name}. Exactly one available dialect
	 * should accept a database.
	 *
	 * @param metaData metadata of an open connection
	 * @return whether this dialect handles the database
	 * @throws SQLException when the metadata cannot be read
	 */
	boolean accepts(@NotNull DatabaseMetaData metaData) throws SQLException;

	/**
	 * Returns the renderer of this dialect's schema changes.
	 *
	 * @return the schema renderer
	 */
	@NotNull SchemaRenderer schemaRenderer();

	/**
	 * Returns the renderer of this dialect's entity statements.
	 *
	 * @return the statement renderer
	 */
	@NotNull StatementRenderer statementRenderer();

	/**
	 * Writes a table, column or index name the way this dialect's renderers write it: as given when
	 * it is a lowercase snake_case word that no supported database reserves, quoted otherwise. Hand
	 * written queries can use it to name things exactly like the generated ones.
	 *
	 * @param name name as declared
	 * @return the name as written in SQL
	 */
	@NotNull String identifier(@NotNull String name);

	/**
	 * Binds a value of a column type as a statement parameter, in the form this dialect stores it;
	 * SQLite and MySQL, for example, store a UUID as text, and SQLite an instant as text.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param type      column type the value belongs to
	 * @param value     value to bind, of the Java type that matches {@code type}, or {@code null}
	 * @throws SQLException when the driver refuses the value
	 */
	void bind(@NotNull PreparedStatement statement, int index, @NotNull ColumnType type, @Nullable Object value) throws SQLException;

	/**
	 * Reads a value of a column type from the current row, converting it from the form this dialect
	 * stores it in to the Java type that matches {@code type}: {@link java.util.UUID},
	 * {@link String}, {@link Long}, {@link Integer}, {@link Boolean}, {@link java.time.Instant} or
	 * {@link java.time.LocalDate}.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @param type   column type to read
	 * @return the value, or {@code null} for SQL {@code NULL}
	 * @throws SQLException when the value cannot be read
	 */
	@Nullable Object read(@NotNull ResultSet row, int column, @NotNull ColumnType type) throws SQLException;

	/**
	 * Tells whether an exception reports that a statement was refused because a row already holds
	 * a primary key or unique key value the statement writes. The entity statements that resolve a
	 * conflict by failing, the {@link StatementRenderer#insertIfAbsent(me.whereareiam.dialectica.schema.TableDefinition) insert if absent}
	 * of MySQL, MariaDB and H2 and the {@link me.whereareiam.dialectica.dialect.Upsert.InsertThenUpdate insert of an upsert},
	 * fail with an exception this recognises.
	 *
	 * @param exception exception a statement failed with
	 * @return whether it reports a key conflict
	 */
	boolean isKeyConflict(@NotNull SQLException exception);

	/**
	 * Returns the dialects that the class loader of Dialectica's API can see.
	 *
	 * @return the available dialects, in discovery order
	 */
	static @NotNull List<Dialect> available() {
		List<Dialect> dialects = new ArrayList<>();
		ServiceLoader.load(Dialect.class, Dialect.class.getClassLoader()).forEach(dialects::add);

		return dialects;
	}

	/**
	 * Finds the available dialect that accepts the database behind a connection.
	 *
	 * @param metaData metadata of an open connection
	 * @return the accepting dialect
	 * @throws SQLException             when the metadata cannot be read
	 * @throws IllegalArgumentException when no available dialect accepts the database
	 * @throws IllegalStateException    when several available dialects accept it
	 */
	static @NotNull Dialect of(@NotNull DatabaseMetaData metaData) throws SQLException {
		List<Dialect> available = available();
		List<Dialect> accepting = new ArrayList<>();
		for (Dialect dialect : available)
			if (dialect.accepts(metaData)) accepting.add(dialect);

		if (accepting.size() == 1) return accepting.get(0);

		List<String> names = new ArrayList<>();
		for (Dialect dialect : accepting.isEmpty() ? available : accepting)
			names.add(dialect.name());
		if (accepting.isEmpty())
			throw new IllegalArgumentException("No dialect accepts the database " + metaData.getDatabaseProductName()
					+ "; available dialects: " + names);

		throw new IllegalStateException("Several dialects accept the database " + metaData.getDatabaseProductName() + ": " + names);
	}
}
