package me.whereareiam.dialectica.adapter.jdbi;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.adapter.jdbi.binding.DialectArgumentFactory;
import me.whereareiam.dialectica.adapter.jdbi.binding.DialectColumnMapperFactory;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Makes a {@code Jdbi} speak a Dialectica {@link Dialect}: it binds and maps {@link java.util.UUID},
 * {@link Boolean}, {@link java.time.Instant} and {@link java.time.LocalDate} values the way the
 * dialect stores them, so a UUID becomes text on SQLite and {@code CHAR(36)} on MySQL, and an
 * instant ISO-8601 text on SQLite and a {@code DATETIME(6)} in UTC on MySQL, and it lets {@link EntityAccess} and {@link RegisterDialecticaMapper}
 * find the dialect.
 * <pre>{@code
 * Jdbi jdbi = Jdbi.create(dataSource);
 * jdbi.installPlugin(new SqlObjectPlugin());
 * jdbi.installPlugin(new DialecticaPlugin());
 * }</pre>
 */
public final class DialecticaPlugin implements JdbiPlugin {
	private final Dialect dialect;

	/**
	 * Creates a plugin that detects the dialect from a connection when it is installed, which
	 * opens one connection.
	 */
	public DialecticaPlugin() {
		this.dialect = null;
	}

	/**
	 * Creates a plugin for a known dialect.
	 *
	 * @param dialect dialect of the database
	 */
	public DialecticaPlugin(@NotNull Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public void customizeJdbi(@NotNull Jdbi jdbi) throws SQLException {
		Dialect chosen = dialect != null
				? dialect
				: jdbi.withHandle(handle -> Dialect.of(handle.getConnection().getMetaData()));

		jdbi.getConfig(DialecticaConfig.class).dialect(chosen);
		jdbi.registerArgument(new DialectArgumentFactory());
		jdbi.registerColumnMapper(new DialectColumnMapperFactory());
	}
}
